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
package org.apache.shindig.gadgets.rewrite;

/**
 * Exceptions thrown during content rewriting.
 *
 * These exceptions will usually translate directly into an end-user error message, so they should
 * be easily localizable.
 */
public class RewritingException extends Exception {
  private final int httpStatusCode;
  
  public RewritingException(Throwable t, int httpStatusCode) {
    super(t);
    this.httpStatusCode = httpStatusCode;
  }

  public RewritingException(String message, int httpStatusCode) {
    super(message);
    this.httpStatusCode = httpStatusCode;
  }

  public RewritingException(String message, Throwable t, int httpStatusCode) {
    super(message, t);
    this.httpStatusCode = httpStatusCode;
  }

  public int getHttpStatusCode() {
    return httpStatusCode;
  }
}
