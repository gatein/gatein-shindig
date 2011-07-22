/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shindig.gadgets.servlet;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.StringUtils;
import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.config.AbstractContainerConfig;
import org.apache.shindig.config.ContainerConfig;
import org.apache.shindig.gadgets.http.HttpRequest;
import org.apache.shindig.gadgets.http.HttpResponse;
import org.apache.shindig.gadgets.http.HttpResponseBuilder;
import org.apache.shindig.gadgets.rewrite.CaptureRewriter;
import org.apache.shindig.gadgets.rewrite.DefaultResponseRewriterRegistry;
import org.apache.shindig.gadgets.rewrite.ResponseRewriter;
import org.apache.shindig.gadgets.uri.AccelUriManager;
import org.apache.shindig.gadgets.uri.DefaultAccelUriManager;
import org.apache.shindig.gadgets.uri.DefaultProxyUriManager;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Vector;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;

public class HtmlAccelServletTest extends ServletTestFixture {

  private static class FakeContainerConfig extends AbstractContainerConfig {
    protected final Map<String, Object> data = ImmutableMap.<String, Object>builder()
        .put(AccelUriManager.PROXY_HOST_PARAM, "apache.org")
        .put(AccelUriManager.PROXY_PATH_PARAM, "/gadgets/accel")
        .build();

    @Override
    public Object getProperty(String container, String name) {
      return data.get(name);
    }
  }

  private static class FakeCaptureRewriter extends CaptureRewriter {
    String contentToRewrite;

    public void setContentToRewrite(String s) {
      contentToRewrite = s;
    }
    @Override
    public void rewrite(HttpRequest request, HttpResponseBuilder original) {
      super.rewrite(request, original);
      if (!StringUtils.isEmpty(contentToRewrite)) {
        original.setResponse(contentToRewrite.getBytes());
      }
    }
  }

  private static final String REWRITE_CONTENT = "working rewrite";
  private static final String SERVLET = "/gadgets/accel";
  private HtmlAccelServlet servlet;

  @Before
  public void setUp() throws Exception {
    ContainerConfig config = new FakeContainerConfig();
    AccelUriManager accelUriManager = new DefaultAccelUriManager(
        config, new DefaultProxyUriManager(config, null));

    rewriter = new FakeCaptureRewriter();
    rewriterRegistry = new DefaultResponseRewriterRegistry(
        Arrays.<ResponseRewriter>asList(rewriter), null);
    servlet = new HtmlAccelServlet();
    servlet.setHandler(new AccelHandler(pipeline, rewriterRegistry,
                                        accelUriManager, true));
  }

  private void expectRequest(String extraPath, String url) {
    expect(request.getMethod()).andReturn("GET").anyTimes();
    expect(request.getServletPath()).andReturn(SERVLET).anyTimes();
    expect(request.getScheme()).andReturn("http").anyTimes();
    expect(request.getServerName()).andReturn("apache.org").anyTimes();
    expect(request.getServerPort()).andReturn(-1).anyTimes();
    expect(request.getRequestURI()).andReturn(SERVLET + extraPath).anyTimes();
    expect(request.getRequestURL())
        .andReturn(new StringBuffer("apache.org" + SERVLET + extraPath))
        .anyTimes();
    String queryParams = (url == null ? "" : "url=" + url + "&container=accel"
                                             + "&gadget=test");
    expect(request.getQueryString()).andReturn(queryParams).anyTimes();
    Vector<String> headerNames = new Vector<String>();
    expect(request.getHeaderNames()).andReturn(headerNames.elements());
  }

  private void expectPostRequest(String extraPath, String url,
                                 final String data)
      throws IOException {
    expect(request.getMethod()).andReturn("POST").anyTimes();
    expect(request.getServletPath()).andReturn(SERVLET).anyTimes();
    expect(request.getScheme()).andReturn("http").anyTimes();
    expect(request.getServerName()).andReturn("apache.org").anyTimes();
    expect(request.getServerPort()).andReturn(-1).anyTimes();
    expect(request.getRequestURI()).andReturn(SERVLET + extraPath).anyTimes();
    expect(request.getRequestURL())
        .andReturn(new StringBuffer("apache.org" + SERVLET + extraPath))
        .anyTimes();
    String queryParams = (url == null ? "" : "url=" + url + "&container=accel"
                                             + "&gadget=test");
    expect(request.getQueryString()).andReturn(queryParams).anyTimes();

    ServletInputStream inputStream = mock(ServletInputStream.class);
    expect(request.getInputStream()).andReturn(inputStream);
    expect(inputStream.read((byte[]) EasyMock.anyObject()))
        .andAnswer(new IAnswer<Integer>() {
          public Integer answer() throws Throwable {
            byte[] byteArray = (byte[]) EasyMock.getCurrentArguments()[0];
            System.arraycopy(data.getBytes(), 0, byteArray, 0, data.length());
            return data.length();
          }
        });
    expect(inputStream.read((byte[]) EasyMock.anyObject()))
        .andReturn(-1);
    Vector<String> headerNames = new Vector<String>();
    expect(request.getHeaderNames()).andReturn(headerNames.elements());
  }

  @Test
  public void testHtmlAccelNoData() throws Exception {
    String url = "http://example.org/data.html";

    HttpRequest req = new HttpRequest(Uri.parse(url));
    expect(pipeline.execute(req)).andReturn(null).once();
    expectRequest("", url);
    replay();

    servlet.doGet(request, recorder);
    verify();
    assertEquals(AccelHandler.ERROR_FETCHING_DATA,
                 recorder.getResponseAsString());
    assertEquals(404, recorder.getHttpStatusCode());
  }

  @Test
  public void testHtmlAccelRewriteSimple() throws Exception {
    String url = "http://example.org/data.html";
    String data = "<html><body>Hello World</body></html>";

    ((FakeCaptureRewriter) rewriter).setContentToRewrite(REWRITE_CONTENT);
    HttpRequest req = new HttpRequest(Uri.parse(url));
    HttpResponse resp = new HttpResponseBuilder()
        .setResponse(data.getBytes())
        .setHeader("Content-Type", "text/html")
        .setHttpStatusCode(200)
        .create();
    expect(pipeline.execute(req)).andReturn(resp).once();
    expectRequest("", url);
    replay();

    servlet.doGet(request, recorder);
    verify();
    assertEquals(REWRITE_CONTENT, recorder.getResponseAsString());
    assertEquals(200, recorder.getHttpStatusCode());
    assertTrue(rewriter.responseWasRewritten());
  }

  @Test
  public void testHtmlAccelRewriteDoesNotFollowRedirects() throws Exception {
    String url = "http://example.org/data.html";
    String data = "<html><body>Moved to new page</body></html>";
    String redirectLocation = "http://example-redirected.org/data.html";

    ((FakeCaptureRewriter) rewriter).setContentToRewrite(data);
    HttpRequest req = new HttpRequest(Uri.parse(url));
    HttpResponse resp = new HttpResponseBuilder()
        .setResponse(data.getBytes())
        .setHeader("Content-Type", "text/html")
        .setHeader("Location", redirectLocation)
        .setHttpStatusCode(302)
        .create();
    expect(pipeline.execute(req)).andReturn(resp).once();
    expectRequest("", url);
    replay();

    servlet.doGet(request, recorder);
    verify();
    assertEquals(data, recorder.getResponseAsString());
    assertEquals(redirectLocation, recorder.getHeader("Location"));
    assertEquals(302, recorder.getHttpStatusCode());
    assertTrue(rewriter.responseWasRewritten());
  }

  @Test
  public void testHtmlAccelReturnsOriginal404MessageAndCode() throws Exception {
    String url = "http://example.org/data.html";
    String data = "<html><body>This is error page</body></html>";

    ((FakeCaptureRewriter) rewriter).setContentToRewrite(REWRITE_CONTENT);
    HttpRequest req = new HttpRequest(Uri.parse(url));
    HttpResponse resp = new HttpResponseBuilder()
        .setResponse(data.getBytes())
        .setHeader("Content-Type", "text/html")
        .setHttpStatusCode(404)
        .create();
    expect(pipeline.execute(req)).andReturn(resp).once();
    expectRequest("", url);
    replay();

    servlet.doGet(request, recorder);
    verify();
    assertEquals(data, recorder.getResponseAsString());
    assertEquals(404, recorder.getHttpStatusCode());
    assertFalse(rewriter.responseWasRewritten());
  }

  @Test
  public void testHtmlAccelRewriteInternalError() throws Exception {
    String url = "http://example.org/data.html";
    String data = "<html><body>This is error page</body></html>";

    ((FakeCaptureRewriter) rewriter).setContentToRewrite(data);
    HttpRequest req = new HttpRequest(Uri.parse(url));
    HttpResponse resp = new HttpResponseBuilder()
        .setResponse(data.getBytes())
        .setHeader("Content-Type", "text/html")
        .setHttpStatusCode(500)
        .create();
    expect(pipeline.execute(req)).andReturn(resp).once();
    expectRequest("", url);
    replay();

    servlet.doGet(request, recorder);
    verify();
    assertEquals(data, recorder.getResponseAsString());
    assertEquals(502, recorder.getHttpStatusCode());
    assertFalse(rewriter.responseWasRewritten());
  }

  @Test
  public void testHtmlAccelHandlesPost() throws Exception {
    String url = "http://example.org/data.html";
    String data = "<html><body>This is error page</body></html>";

    ((FakeCaptureRewriter) rewriter).setContentToRewrite(data);
    Capture<HttpRequest> req = new Capture<HttpRequest>();
    HttpResponse resp = new HttpResponseBuilder()
        .setResponse(data.getBytes())
        .setHeader("Content-Type", "text/html")
        .create();
    expect(pipeline.execute(capture(req))).andReturn(resp).once();
    expectPostRequest("", url, "hello=world");
    replay();

    servlet.doPost(request, recorder);
    verify();
    assertEquals(data, recorder.getResponseAsString());
    assertEquals(200, recorder.getHttpStatusCode());
    assertTrue(rewriter.responseWasRewritten());
    assertEquals("POST", req.getValue().getMethod());
    assertEquals("hello=world", req.getValue().getPostBodyAsString());
  }

  @Test
  public void testHtmlAccelReturnsSameStatusCodeAndMessageWhenError() throws Exception {
    String url = "http://example.org/data.html";
    String data = "<html><body>This is error page</body></html>";

    ((FakeCaptureRewriter) rewriter).setContentToRewrite(data);
    HttpRequest req = new HttpRequest(Uri.parse(url));
    HttpResponse resp = new HttpResponseBuilder()
        .setResponse(data.getBytes())
        .setHeader("Content-Type", "text/html")
        .setHttpStatusCode(5001)
        .create();
    expect(pipeline.execute(req)).andReturn(resp).once();
    expectRequest("", url);
    replay();

    servlet.doGet(request, recorder);
    verify();
    assertEquals(data, recorder.getResponseAsString());
    assertEquals(5001, recorder.getHttpStatusCode());
    assertFalse(rewriter.responseWasRewritten());
  }
}
