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

package io.vertx.ext.web.templ;

import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.WebTestBase;
import io.vertx.ext.web.templ.impl.CachingTemplateEngine;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JadeTemplateTest extends WebTestBase {

  protected VertxOptions getOptions() {
    return new VertxOptions().setFileResolverCachingEnabled(true);
  }

  @Test
  public void testTemplateHandlerOnClasspath() throws Exception {
    TemplateEngine engine = JadeTemplateEngine.create();
    testTemplateHandler(engine, "somedir", "test-jade-template2.jade", "<!DOCTYPE html><html><head><title>badger/test-jade-template2.jade</title></head><body></body></html>");
  }

  @Test
  public void testTemplateHandlerOnFileSystem() throws Exception {
    TemplateEngine engine = JadeTemplateEngine.create();
    testTemplateHandler(engine, "src/test/filesystemtemplates", "test-jade-template3.jade", "<!DOCTYPE html><html><head><title>badger/test-jade-template3.jade</title></head><body></body></html>");
  }

  @Test
  public void testTemplateHandlerOnClasspathDisableCaching() throws Exception {
    System.setProperty(CachingTemplateEngine.DISABLE_TEMPL_CACHING_PROP_NAME, "true");
    testTemplateHandlerOnClasspath();
  }

  @Test
  public void testTemplateHandlerNoExtension() throws Exception {
    TemplateEngine engine = JadeTemplateEngine.create();
    testTemplateHandler(engine, "somedir", "test-jade-template2", "<!DOCTYPE html><html><head><title>badger/test-jade-template2</title></head><body></body></html>");
  }

  @Test
  public void testTemplateHandlerChangeExtension() throws Exception {
    TemplateEngine engine = JadeTemplateEngine.create().setExtension("made");
    testTemplateHandler(engine, "somedir", "test-jade-template2", "<!DOCTYPE html><html><head><title>aardvark/test-jade-template2</title></head><body></body></html>");
  }

  private void testTemplateHandler(TemplateEngine engine, String directoryName, String templateName,
                                   String expected) throws Exception {
    router.route().handler(context -> {
      context.put("foo", "badger");
      context.next();
    });
    router.route().handler(TemplateHandler.create(engine, directoryName, "text/plain"));
    testRequest(HttpMethod.GET, "/" + templateName, 200, "OK", expected);
  }

  @Test
  public void testNoSuchTemplate() throws Exception {
    TemplateEngine engine = JadeTemplateEngine.create();
    router.route().handler(TemplateHandler.create(engine, "somedir", "text/plain"));
    testRequest(HttpMethod.GET, "/foo.jade", 500, "Internal Server Error");
  }

  @Test
  public void testGetJadeConfiguration() throws Exception {
    JadeTemplateEngine engine = JadeTemplateEngine.create();
    assertNotNull(engine.getJadeConfiguration());
  }

  @Test
  public void testCachingEnabled() throws Exception {
    System.setProperty(CachingTemplateEngine.DISABLE_TEMPL_CACHING_PROP_NAME, "false");
    TemplateEngine engine = JadeTemplateEngine.create();

    PrintWriter out;
    File temp = File.createTempFile("template", ".jade", new File("target/classes"));
    temp.deleteOnExit();

    out = new PrintWriter(temp);
    out.print("before");
    out.flush();
    out.close();

    testTemplateHandler(engine, ".", temp.getName(), "<before></before>");

    // cache is enabled so if we change the content that should not affect the result

    out = new PrintWriter(temp);
    out.print("after");
    out.flush();
    out.close();

    testTemplateHandler(engine, ".", temp.getName(), "<before></before>");
  }
}
