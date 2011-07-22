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
package org.apache.shindig.gadgets.uri;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.shindig.common.util.Base32;
import org.apache.shindig.common.uri.Uri;

/**
 * A simple implementation of locked domain that hashes the gadgeturi as the prefix.
 */
public class HashShaLockedDomainPrefixGenerator implements LockedDomainPrefixGenerator {
  public String getLockedDomainPrefix(Uri gadgetUri) {
    byte[] sha1 = DigestUtils.sha(gadgetUri.toString());
    return new String(Base32.encodeBase32(sha1)); // a hash
  }
}
