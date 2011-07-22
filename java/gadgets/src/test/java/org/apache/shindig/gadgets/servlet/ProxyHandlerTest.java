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

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

import org.apache.shindig.common.EasyMockTestCase;
import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.config.ContainerConfig;
import org.apache.shindig.gadgets.Gadget;
import org.apache.shindig.gadgets.GadgetException;
import org.apache.shindig.gadgets.http.HttpRequest;
import org.apache.shindig.gadgets.http.HttpResponse;
import org.apache.shindig.gadgets.http.HttpResponseBuilder;
import org.apache.shindig.gadgets.http.RequestPipeline;
import org.apache.shindig.gadgets.rewrite.CaptureRewriter;
import org.apache.shindig.gadgets.rewrite.DefaultResponseRewriterRegistry;
import org.apache.shindig.gadgets.rewrite.DomWalker;
import org.apache.shindig.gadgets.rewrite.MutableContent;
import org.apache.shindig.gadgets.rewrite.ResponseRewriter;
import org.apache.shindig.gadgets.rewrite.ResponseRewriterRegistry;
import org.apache.shindig.gadgets.rewrite.RewritingException;
import org.apache.shindig.gadgets.uri.ProxyUriManager;
import org.apache.shindig.gadgets.uri.UriCommon.Param;
import org.easymock.Capture;
import org.easymock.EasyMock;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ProxyHandlerTest extends EasyMockTestCase {
  private final static String URL_ONE = "http://www.example.org/test.html";
  private final static String DATA_ONE = "hello world";

  public final RequestPipeline pipeline = mock(RequestPipeline.class);
  public CaptureRewriter rewriter = new CaptureRewriter();
  public ResponseRewriterRegistry rewriterRegistry
      = new DefaultResponseRewriterRegistry(Arrays.<ResponseRewriter>asList(rewriter), null);
  private ProxyUriManager.ProxyUri request;
  
  private final ProxyHandler proxyHandler
      = new ProxyHandler(pipeline, rewriterRegistry, true);
  
  private void expectGetAndReturnData(String url, byte[] data) throws Exception {
    HttpRequest req = new HttpRequest(Uri.parse(url));
    HttpResponse resp = new HttpResponseBuilder().setResponse(data).create();
    expect(pipeline.execute(req)).andReturn(resp);
  }

  private void expectGetAndReturnHeaders(String url, Map<String, List<String>> headers)
      throws Exception {
    HttpRequest req = new HttpRequest(Uri.parse(url));
    HttpResponse resp = new HttpResponseBuilder().addAllHeaders(headers).create();
    expect(pipeline.execute(req)).andReturn(resp);
  }
  
  private void setupProxyRequestMock(String host, String url,
      boolean noCache, int refresh, String rewriteMime, String fallbackUrl) throws Exception {
    request = new ProxyUriManager.ProxyUri(
        refresh, false, noCache, ContainerConfig.DEFAULT_CONTAINER, null, Uri.parse(url));
    request.setFallbackUrl(fallbackUrl);
    request.setRewriteMimeType(rewriteMime);
  }

  private void setupNoArgsProxyRequestMock(String host, String url) throws Exception {
    request = new ProxyUriManager.ProxyUri(
        -1, false, false, ContainerConfig.DEFAULT_CONTAINER, null,
        url != null ? Uri.parse(url) : null);
  }

  private ResponseRewriter getResponseRewriterThatThrowsExceptions(
      final StringBuilder stringBuilder) {
    return new DomWalker.Rewriter() {
      public void rewrite(Gadget gadget, MutableContent content)
          throws RewritingException {
        stringBuilder.append("exceptionThrown");
        throw new RewritingException("sad", 404);
      }

      public void rewrite(HttpRequest request, HttpResponseBuilder builder)
          throws RewritingException {
        stringBuilder.append("exceptionThrown");
        throw new RewritingException("sad", 404);
      }
    };
  }

  @Test
  public void testInvalidHeaderDropped() throws Exception {
    String url = "http://example.org/mypage.html";
    String domain = "example.org";

    setupProxyRequestMock(domain, url, true, -1, null, null);

    HttpRequest req = new HttpRequest(Uri.parse(url))
        .setIgnoreCache(true);
    String contentType = "text/html; charset=UTF-8";
    HttpResponse resp = new HttpResponseBuilder()
        .setResponseString("Hello")
        .addHeader("Content-Type", contentType)
        .addHeader("Content-Length", "200")  // Disallowed header.
        .addHeader(":", "someDummyValue") // Invalid header name.
        .create();

    expect(pipeline.execute(req)).andReturn(resp);

    replay();

    HttpResponse recorder = proxyHandler.fetch(request);

    verify();
    assertNull(recorder.getHeader(":"));
    assertNull(recorder.getHeader("Content-Length"));
    assertEquals(recorder.getHeader("Content-Type"), contentType);
  }

  @Test
  public void testLockedDomainEmbed() throws Exception {
    setupNoArgsProxyRequestMock("www.example.com", URL_ONE);
    expectGetAndReturnData(URL_ONE, DATA_ONE.getBytes());
   
    replay();
    HttpResponse response = proxyHandler.fetch(request);
    verify();

    assertEquals(DATA_ONE, response.getResponseAsString());
    assertTrue(rewriter.responseWasRewritten());
  }

  @Test(expected=GadgetException.class)
  public void testNoUrl() throws Exception {
    setupNoArgsProxyRequestMock("www.example.com", null);
    replay();

    proxyHandler.fetch(request);
    fail("Proxy should raise exception if there is no url");
  }

  @Test
  public void testHttpRequestFillsParentAndContainer() throws Exception {
    setupNoArgsProxyRequestMock("www.example.com", URL_ONE);
    //HttpRequest req = new HttpRequest(Uri.parse(URL_ONE));
    HttpResponse resp = new HttpResponseBuilder().setResponse(DATA_ONE.getBytes()).create();

    Capture<HttpRequest> httpRequest = new Capture<HttpRequest>();
    expect(pipeline.execute(capture(httpRequest))).andReturn(resp);

    replay();
    HttpResponse response = proxyHandler.fetch(request);
    verify();

    // Check that the HttpRequest passed in has all the relevant fields sets
    assertEquals("default", httpRequest.getValue().getContainer());
    assertEquals(Uri.parse(URL_ONE), httpRequest.getValue().getUri());

    assertEquals(DATA_ONE, response.getResponseAsString());
    assertTrue(rewriter.responseWasRewritten());
  }

  @Test
  public void testHeadersPreserved() throws Exception {
    // Some headers may be blacklisted. These are OK.
    String url = "http://example.org/file.evil";
    String domain = "example.org";
    String contentType = "text/evil; charset=UTF-8";
    String magicGarbage = "fadfdfdfd";
    Map<String, List<String>> headers = Maps.newHashMap();
    headers.put("Content-Type", Arrays.asList(contentType));
    headers.put("X-Magic-Garbage", Arrays.asList(magicGarbage));

    setupNoArgsProxyRequestMock(domain, url);
    expectGetAndReturnHeaders(url, headers);

    replay();
    HttpResponse response = proxyHandler.fetch(request);
    verify();

    assertEquals(contentType, response.getHeader("Content-Type"));
    assertEquals(magicGarbage, response.getHeader("X-Magic-Garbage"));
    assertTrue(rewriter.responseWasRewritten());
  }
  
  @Test
  public void testOctetSetOnNullContentType() throws Exception {
    String url = "http://example.org/file.evil";
    String domain = "example.org";

    setupNoArgsProxyRequestMock(domain, url);
    expectGetAndReturnHeaders(url, Maps.<String, List<String>>newHashMap());

    replay();
    HttpResponse response = proxyHandler.fetch(request);
    verify();

    assertEquals("application/octet-stream", response.getHeader("Content-Type"));
    assertNotNull(response.getHeader("Content-Disposition"));
    assertTrue(rewriter.responseWasRewritten());
  }
  
  @Test
  public void testNoContentDispositionForFlash() throws Exception {
    // Some headers may be blacklisted. These are OK.
    String url = "http://example.org/file.evil";
    String domain = "example.org";
    Map<String, List<String>> headers = Maps.newHashMap();
    headers.put("Content-Type", Arrays.asList("application/x-shockwave-flash"));

    setupNoArgsProxyRequestMock(domain, url);
    expectGetAndReturnHeaders(url, headers);

    replay();
    HttpResponse response = proxyHandler.fetch(request);
    verify();

    assertEquals("application/x-shockwave-flash", response.getHeader("Content-Type"));
    assertNull(response.getHeader("Content-Disposition"));
    assertTrue(rewriter.responseWasRewritten());
  }
  
  @Test
  public void testGetFallback() throws Exception {
    String url = "http://example.org/file.evil";
    String domain = "example.org";
    String fallback_url = "http://fallback.com/fallback.png";

    setupProxyRequestMock(domain, url, true, -1, null, fallback_url);

    HttpRequest req = new HttpRequest(Uri.parse(url)).setIgnoreCache(true);
    HttpResponse resp = HttpResponse.error();
    HttpResponse fallback_resp = new HttpResponse("Fallback");
    expect(pipeline.execute(req)).andReturn(resp);
    expect(pipeline.execute(isA(HttpRequest.class))).andReturn(fallback_resp);

    replay();
    proxyHandler.fetch(request);
    verify();
  }

  @Test
  public void testNoCache() throws Exception {
    String url = "http://example.org/file.evil";
    String domain = "example.org";

    setupProxyRequestMock(domain, url, true, -1, null, null);

    HttpRequest req = new HttpRequest(Uri.parse(url)).setIgnoreCache(true);
    HttpResponse resp = new HttpResponse("Hello");
    expect(pipeline.execute(req)).andReturn(resp);

    replay();
    proxyHandler.fetch(request);
    verify();
  }

  // ProxyHandler throws INTERNAL_SERVER_ERRORS without isRecoverable() check.
  @Test
  public void testRecoverableRewritingException() throws Exception {
    String url = "http://example.org/mypage.html";
    String domain = "example.org";

    setupProxyRequestMock(domain, url, true, -1, null, null);

    String contentType = "text/html; charset=UTF-8";
    HttpResponse resp = new HttpResponseBuilder()
        .setResponseString("Hello")
        .addHeader("Content-Type", contentType)
        .create();

    expect(pipeline.execute((HttpRequest) EasyMock.anyObject())).andReturn(resp);

    replay();

    final StringBuilder stringBuilder = new StringBuilder("");
    ResponseRewriter rewriter = getResponseRewriterThatThrowsExceptions(stringBuilder);

    ResponseRewriterRegistry rewriterRegistry =
        new DefaultResponseRewriterRegistry(
            Arrays.<ResponseRewriter>asList(rewriter), null);
    ProxyHandler proxyHandler = new ProxyHandler(pipeline, rewriterRegistry, true);

    request.setReturnOriginalContentOnError(true);
    HttpResponse recorder = proxyHandler.fetch(request);

    verify();

    // Ensure that original content is returned.
    assertEquals(recorder.getHeader("Content-Type"), contentType);
    assertEquals("Hello", recorder.getResponseAsString());
    assertEquals("exceptionThrown", stringBuilder.toString());
  }

  @Test
  public void testThrowExceptionIfReturnOriginalContentOnErrorNotSet()
      throws Exception {
    String url = "http://example.org/mypage.html";
    String domain = "example.org";

    setupProxyRequestMock(domain, url, true, -1, null, null);

    String contentType = "text/html; charset=UTF-8";
    HttpResponse resp = new HttpResponseBuilder()
        .setResponseString("Hello")
        .addHeader("Content-Type", contentType)
        .create();

    expect(pipeline.execute((HttpRequest) EasyMock.anyObject())).andReturn(resp);

    replay();

    final StringBuilder stringBuilder = new StringBuilder("");
    ResponseRewriter rewriter = getResponseRewriterThatThrowsExceptions(stringBuilder);

    ResponseRewriterRegistry rewriterRegistry =
        new DefaultResponseRewriterRegistry(
            Arrays.<ResponseRewriter>asList(rewriter), null);
    ProxyHandler proxyHandler = new ProxyHandler(pipeline, rewriterRegistry, true);

    boolean exceptionCaught = false;
    try {
      proxyHandler.fetch(request);
    } catch (GadgetException e) {
      exceptionCaught = true;
      assertEquals(404, e.getHttpStatusCode());
    }
    assertTrue(exceptionCaught);
    assertEquals("exceptionThrown", stringBuilder.toString());
  }

  /**
   * Override HttpRequest equals to check for cache control fields
   */
  static class HttpRequestCache extends HttpRequest {
    public HttpRequestCache(Uri uri) {
      super(uri);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof HttpRequest)) {
        return false;
      }
      HttpRequest req = (HttpRequest)obj;
      return super.equals(obj) && req.getCacheTtl() == getCacheTtl() &&
              req.getIgnoreCache() == getIgnoreCache();
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(super.hashCode(), getCacheTtl(), getIgnoreCache());
    }
  }

  @Test
  public void testWithCache() throws Exception {
    String url = "http://example.org/file.evil";
    String domain = "example.org";

    setupProxyRequestMock(domain, url, false, 120, null, null);
    
    HttpRequest req = new HttpRequestCache(Uri.parse(url)).setCacheTtl(120).setIgnoreCache(false);
    HttpResponse resp = new HttpResponse("Hello");
    expect(pipeline.execute(req)).andReturn(resp);

    replay();
    proxyHandler.fetch(request);
    verify();
  }

  @Test
  public void testWithBadTtl() throws Exception {
    String url = "http://example.org/file.evil";
    String domain = "example.org";

    setupProxyRequestMock(domain, url, false, -1, null, null);
    
    HttpRequest req = new HttpRequestCache(Uri.parse(url)).setCacheTtl(-1).setIgnoreCache(false);
    HttpResponse resp = new HttpResponse("Hello");
    expect(pipeline.execute(req)).andReturn(resp);

    replay();
    proxyHandler.fetch(request);
    verify();
  }

  private void expectMime(String expectedMime, String contentMime, String outputMime)
      throws Exception {
    String url = "http://example.org/file.img?" + Param.REWRITE_MIME_TYPE.getKey() +
        '=' + expectedMime;
    String domain = "example.org";

    setupProxyRequestMock(domain, url, false, -1, expectedMime, null);
    
    HttpRequest req = new HttpRequest(Uri.parse(url))
        .setRewriteMimeType(expectedMime);

    HttpResponse resp = new HttpResponseBuilder()
      .setResponseString("Hello")
      .addHeader("Content-Type", contentMime)
      .create();

    expect(pipeline.execute(req)).andReturn(resp);
    
    replay();
    HttpResponse response = proxyHandler.fetch(request);
    verify();
    
    assertEquals(outputMime, response.getHeader("Content-Type"));
    reset();
  }

  @Test
  public void testMimeMatchPass() throws Exception {
    expectMime("text/css", "text/css", "text/css; charset=UTF-8");
  }

  @Test
  public void testMimeMatchPassWithAdditionalAttributes() throws Exception {
    expectMime("text/css", "text/css", "text/css; charset=UTF-8");
  }

  @Test
  public void testMimeMatchOverrideNonMatch() throws Exception {
    expectMime("text/css", "image/png", "text/css; charset=UTF-8");
  }

  @Test
  public void testMimeMatchVarySupport() throws Exception {
    // We use CaptureRewrite which always rewrite - always set encoding
    expectMime("image/*", "image/gif", "image/gif");
  }
}
