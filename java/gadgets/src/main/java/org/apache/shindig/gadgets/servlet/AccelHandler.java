/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.shindig.gadgets.servlet;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.gadgets.Gadget;
import org.apache.shindig.gadgets.GadgetException;
import org.apache.shindig.gadgets.http.HttpRequest;
import org.apache.shindig.gadgets.http.HttpResponse;
import org.apache.shindig.gadgets.http.HttpResponseBuilder;
import org.apache.shindig.gadgets.http.RequestPipeline;
import org.apache.shindig.gadgets.rewrite.DomWalker;
import org.apache.shindig.gadgets.rewrite.ResponseRewriterRegistry;
import org.apache.shindig.gadgets.rewrite.RewritingException;
import org.apache.shindig.gadgets.uri.AccelUriManager;
import org.apache.shindig.gadgets.uri.ProxyUriManager;
import org.apache.shindig.gadgets.uri.UriCommon;
import org.apache.shindig.gadgets.uri.UriUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Handles requests for accel servlet.
 * The objective is to accelerate web pages.
 *
 * @since 2.0.0
 */
@Singleton
public class AccelHandler {
  static final String ERROR_FETCHING_DATA = "Error fetching data";
  protected final RequestPipeline requestPipeline;
  protected final ResponseRewriterRegistry contentRewriterRegistry;
  protected final AccelUriManager uriManager;
  protected final boolean remapInternalServerError;

  @Inject
  public AccelHandler(RequestPipeline requestPipeline,
                      @Named("shindig.accelerate.response.rewriter.registry")
                      ResponseRewriterRegistry contentRewriterRegistry,
                      AccelUriManager accelUriManager,
                      @Named("shindig.accelerate.remapInternalServerError")
                      Boolean remapInternalServerError) {
    this.requestPipeline = requestPipeline;
    this.contentRewriterRegistry = contentRewriterRegistry;
    this.uriManager = accelUriManager;
    this.remapInternalServerError = remapInternalServerError;
  }

  protected HttpResponse fetch(HttpRequest request) throws IOException, GadgetException {
    // TODO: Handle if modified since headers.

    // Parse and normalize to get a proxied request uri.
    ProxyUriManager.ProxyUri proxyUri = getProxyUri(request);

    // Fetch the content of the requested uri.
    HttpRequest req = buildHttpRequest(request, proxyUri);
    HttpResponse results = requestPipeline.execute(req);

    HttpResponse errorResponse = handleErrors(results);
    if (errorResponse == null) {
      // No error. Lets rewrite the content.
      try {
        results = contentRewriterRegistry.rewriteHttpResponse(req, results);
      } catch (RewritingException e) {
        if (!isRecoverable(req, results, e)) {
          throw new GadgetException(GadgetException.Code.INTERNAL_SERVER_ERROR, e,
                                    e.getHttpStatusCode());
        }
      }
    } else {
      results = errorResponse;
    }

    // Copy the response headers and status code to the final http servlet
    // response.
    HttpResponseBuilder response = new HttpResponseBuilder();
    UriUtils.copyResponseHeadersAndStatusCode(
        results, response, remapInternalServerError, true,
        UriUtils.DisallowedHeaders.OUTPUT_TRANSFER_DIRECTIVES,
        UriUtils.DisallowedHeaders.CLIENT_STATE_DIRECTIVES);

    // Override the content type of the final http response if the input request
    // had the rewrite mime type header.
    UriUtils.maybeRewriteContentType(req, response);

    // Copy the content.
    // TODO: replace this with streaming APIs when ready
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    IOUtils.copy(results.getResponse(), baos);
    response.setResponseNoCopy(baos.toByteArray());
    return response.create();
  }

  /**
   * Returns the proxy uri encapsulating the request uri.
   * @param httpRequest The http request.
   * @return The proxy uri encapsulating the request uri.
   * @throws GadgetException In case of errors.
   */
  public ProxyUriManager.ProxyUri getProxyUri(HttpRequest httpRequest) throws GadgetException {
    Uri proxiedUri = uriManager.parseAndNormalize(httpRequest);
    String uriString = proxiedUri.getQueryParameter(UriCommon.Param.URL.getKey());

    // Throw BAD_GATEWAY in case parsing of url fails.
    Uri requestedResource;
    try {
      requestedResource = Uri.parse(uriString);
    } catch (Uri.UriException e) {
      throw new GadgetException(GadgetException.Code.INTERNAL_SERVER_ERROR,
                                "Failed to parse uri: " + uriString,
                                HttpResponse.SC_BAD_GATEWAY);
    }

    Gadget gadget = DomWalker.makeGadget(httpRequest);
    ProxyUriManager.ProxyUri proxyUri = new ProxyUriManager.ProxyUri(gadget, requestedResource);
    proxyUri.setHtmlTagContext(proxiedUri.getQueryParameter(
        UriCommon.Param.HTML_TAG_CONTEXT.getKey()));
    return proxyUri;
  }

  /**
   * Returns true in case the error encountered while rewriting the content
   * is recoverable. The rationale behind it is that errors should be thrown
   * only in case of serious grave errors (defined to be un recoverable).
   * It should always be preferred to handle errors and return the original
   * content at least.
   *
   * TODO: Think through all cases which are recoverable to enforce minimal
   * possible set of constraints.
   * TODO: Log the exception and context around it.
   *
   * @param req The http request for fetching the resource.
   * @param results The result of rewriting.
   * @param exception Exception caught.
   * @return True if the error is recoverable, false otherwise.
   */
  protected boolean isRecoverable(HttpRequest req, HttpResponse results,
                                  RewritingException exception) {
    return !(StringUtils.isEmpty(results.getResponseAsString()) &&
             results.getHeaders() == null);
  }

  /**
   * Build an HttpRequest object encapsulating the request details as requested
   * by the user.
   * @param request The http request.
   * @param uriToProxyOrRewrite The parsed uri to proxy or rewrite through
   *   accel servlet.
   * @return Remote content request based on the parameters sent from the client.
   * @throws GadgetException In case the data could not be fetched.
   */
  protected HttpRequest buildHttpRequest(HttpRequest request,
                                         ProxyUriManager.ProxyUri uriToProxyOrRewrite)
      throws GadgetException {
    Uri tgt = uriToProxyOrRewrite.getResource();
    HttpRequest req = uriToProxyOrRewrite.makeHttpRequest(tgt);
    if (req == null) {
      throw new GadgetException(GadgetException.Code.INVALID_PARAMETER,
          "No url parameter in request", HttpResponse.SC_BAD_REQUEST);
    }

    // Copy the post body if it exists.
    UriUtils.copyRequestData(request, req);

    // Set and copy headers.
    ServletUtil.setXForwardedForHeader(request, req);
    
    UriUtils.copyRequestHeaders(
        request, req,
        UriUtils.DisallowedHeaders.POST_INCOMPATIBLE_DIRECTIVES);

    req.setFollowRedirects(false);
    return req;
  }

  /**
   * Process errors when fetching uri using request pipeline and return the
   * error response to be returned to the user if any.
   * @param results The http response returned by request pipeline.
   * @return An HttpResponse instance encapsulating error message and status
   *   code to be returned to the user in case of errors, null otherwise.
   */
  protected HttpResponse handleErrors(HttpResponse results) {
    if (results == null) {
      return new HttpResponseBuilder()
          .setHttpStatusCode(HttpResponse.SC_NOT_FOUND)
          .setResponse(ERROR_FETCHING_DATA.getBytes())
          .create();
    }
    if (results.isError()) {
      return results;
    }

    return null;
  }
}
