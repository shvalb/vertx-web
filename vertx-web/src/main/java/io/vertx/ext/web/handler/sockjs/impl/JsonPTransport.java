/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.web.handler.sockjs.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.ext.web.handler.sockjs.SockJSSocket;
import io.vertx.ext.web.impl.Utils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class JsonPTransport extends BaseTransport {

  private static final Logger log = LoggerFactory.getLogger(JsonPTransport.class);

  JsonPTransport(Vertx vertx, Router router, LocalMap<String, SockJSSession> sessions, SockJSHandlerOptions options,
                 Handler<SockJSSocket> sockHandler) {
    super(vertx, sessions, options);

    String jsonpRE = COMMON_PATH_ELEMENT_RE + "jsonp";

    router.getWithRegex(jsonpRE).handler(rc -> {
      if (log.isTraceEnabled()) log.trace("JsonP, get: " + rc.request().uri());
      String callback = rc.request().getParam("callback");
      if (callback == null) {
        callback = rc.request().getParam("c");
        if (callback == null) {
          rc.response().setStatusCode(500);
          rc.response().end("\"callback\" parameter required\n");
          return;
        }
      }

      HttpServerRequest req = rc.request();
      String sessionID = req.params().get("param0");
      SockJSSession session = getSession(rc, options.getSessionTimeout(), options.getHeartbeatInterval(), sessionID, sockHandler);
      session.setInfo(req.localAddress(), req.remoteAddress(), req.uri(), req.headers());
      session.register(new JsonPListener(rc, session, callback));
    });

    String jsonpSendRE = COMMON_PATH_ELEMENT_RE + "jsonp_send";

    router.postWithRegex(jsonpSendRE).handler(rc -> {
      if (log.isTraceEnabled()) log.trace("JsonP, post: " + rc.request().uri());
      String sessionID = rc.request().getParam("param0");
      final SockJSSession session = sessions.get(sessionID);
      if (session != null && !session.isClosed()) {
        handleSend(rc, session);
      } else {
        rc.response().setStatusCode(404);
        setJSESSIONID(options, rc);
        rc.response().end();
      }
    });
  }

  private void handleSend(RoutingContext rc, SockJSSession session) {
    rc.request().bodyHandler(buff -> {
      String body = buff.toString();

      boolean urlEncoded;
      String ct = rc.request().getHeader("content-type");
      if ("application/x-www-form-urlencoded".equalsIgnoreCase(ct)) {
        urlEncoded = true;
      } else if ("text/plain".equalsIgnoreCase(ct)) {
        urlEncoded = false;
      } else {
        rc.response().setStatusCode(500);
        rc.response().end("Invalid Content-Type");
        return;
      }

      if (body.equals("") || urlEncoded && (!body.startsWith("d=") || body.length() <= 2)) {
        rc.response().setStatusCode(500).end("Payload expected.");
        return;
      }

      if (urlEncoded) {
        body = Utils.urlDecode(body, true).substring(2);
      }

      if (!session.handleMessages(body)) {
        sendInvalidJSON(rc.response());
      } else {
        setJSESSIONID(options, rc);
        rc.response().putHeader("Content-Type", "text/plain; charset=UTF-8");
        setNoCacheHeaders(rc);
        rc.response().end("ok");
        if (log.isTraceEnabled()) log.trace("send handled ok");
      }
    });
  }

  private class JsonPListener extends BaseListener {

    final String callback;
    boolean headersWritten;
    boolean closed;

    JsonPListener(RoutingContext rc, SockJSSession session, String callback) {
      super(rc, session);
      this.callback = callback;
      addCloseHandler(rc.response(), session);
    }


    public void sendFrame(String body) {

      if (log.isTraceEnabled()) log.trace("JsonP, sending frame");

      if (!headersWritten) {
        rc.response().setChunked(true).putHeader("Content-Type", "application/javascript; charset=UTF-8");
        setNoCacheHeaders(rc);
        setJSESSIONID(options, rc);
        headersWritten = true;
      }

      body = escapeForJavaScript(body);

      StringBuilder sb = new StringBuilder();
      sb.append(callback).append("(\"");
      sb.append(body);
      sb.append("\");\r\n");

      //End the response and close the HTTP connection

      rc.response().write(sb.toString());
      close();
    }

    public void close() {
      if (!closed) {
        try {
          session.resetListener();
          rc.response().end();
          rc.response().close();
          closed = true;
        } catch (IllegalStateException e) {
          // Underlying connection might already be closed - that's fine
        }
      }
    }
  }
}
