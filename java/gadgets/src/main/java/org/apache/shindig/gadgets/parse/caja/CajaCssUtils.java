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
package org.apache.shindig.gadgets.parse.caja;

import com.google.caja.parser.AncestorChain;
import com.google.caja.parser.Visitor;
import com.google.caja.parser.css.CssTree;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility functions for traversing Caja's CSS DOM
 */
public final class CajaCssUtils {
  private CajaCssUtils() {}
  
  /**
   * Get the immediate children of the passed node with the specified node type
   */
  public static <T extends CssTree> List<T> children(CssTree node, Class<T> nodeType) {
    List<T> result = Lists.newArrayList();
    for (CssTree child : node.children()) {
      if (nodeType.isAssignableFrom(child.getClass())) {
        result.add(nodeType.cast(child));
      }
    }
    return result;
  }

  /**
   * Get all descendants of the passed node with the specified node type
   */
  public static <T extends CssTree> List<T> descendants(CssTree node, final Class<T> nodeType) {
    final List<T> descendants = Lists.newArrayList();
    node.acceptPreOrder(new Visitor() {
      public boolean visit(AncestorChain<?> ancestorChain) {
        if (nodeType.isAssignableFrom(ancestorChain.node.getClass())) {
          descendants.add(nodeType.cast(ancestorChain.node));
        }
        return true;
      }
    }, null);
    return descendants;
  }

  /**
   * Changes problematic ip6 uris in StyleSheet per indexes before DOM object is serialized.
   * Indexes follow pattern "ipv6i0f", "ipv6i1f", ..., "ipv6iNf".
   *
   * @param styleSheet StyleSheet object representing a CSS DOM to extract ip6 explicit uris
   * @param ip6Uris List of Strings where ip6 uris will be stored
   */
  public static void saveIp6Uris(CssTree.StyleSheet styleSheet, List<String> ip6Uris) {
    if (styleSheet == null || ip6Uris == null) {
        // This case should not happen
        return;
    }
    List<CssTree.UriLiteral> uris = descendants(styleSheet, CssTree.UriLiteral.class);
    for (CssTree.UriLiteral uri : uris) {
        String sUri = uri.getValue();
        String ip6 = extractIp6(sUri);
        if (ip6 != null) {
            int count = ip6Uris.indexOf(ip6);
            if (count == -1) {
                ip6Uris.add(ip6);
                count = ip6Uris.size() - 1;
            }
            String index = "ipv6i" + count + "f";
            uri.setValue(sUri.replace(ip6, index));
        }
    }
  }

  /**
   * Restores original ip6 uris in serialized StyleSheet DOM.
   * Searches patterns "ipv6i0f", "ipv6i1f", ..., "ipv6iNf" and changes them by original ip6 uris.
   *
   * @param styleSheet String representation of StyleSheet object serialized
   * @param ip6Uris List of String with ip6 uris to restore
   * @return String representation of StyleSheet updated with ip6 uris
   */
  public static String restoreIp6Uris(String styleSheet, List<String> ip6Uris) {
    if (ip6Uris == null || ip6Uris.size() == 0) {
        return styleSheet;
    }

    int length = styleSheet.length();
    int i = 0;
    int startToken = -1;
    int finishToken = -1;
    boolean finish = false;
    StringBuilder output = new StringBuilder();

    while (!finish) {
        boolean found = false;
        Character ch = styleSheet.charAt(i);
        // Checks if there is a pattern under present position
        if (ch.equals('i') &&
            ((i+1) < length) && styleSheet.charAt(i+1) == 'p' &&
            ((i+2) < length) && styleSheet.charAt(i+2) == 'v' &&
            ((i+3) < length) && styleSheet.charAt(i+3) == '6' &&
            ((i+4) < length) && styleSheet.charAt(i+4) == 'i') {
            // Search end of token
            boolean numbers = true;
            startToken = i;
            finishToken = i + 5;
            while (numbers) {
                if (finishToken < length &&
                    styleSheet.charAt(finishToken) >= '0' &&
                    styleSheet.charAt(finishToken) <= '9') {
                    finishToken++;
                } else {
                    numbers = false;
                }
            }
            if (finishToken < length &&
                styleSheet.charAt(finishToken) == 'f') {
                String token = styleSheet.substring(startToken, finishToken);
                int index = -1;
                try {
                    index = new Integer(token.substring(5, token.length()));
                } catch (Exception e) {
                    // Not a number
                }
                if (index > -1 && index < ip6Uris.size() ) {
                    output.append(ip6Uris.get(index));
                    found = true;
                    i = finishToken;
                }
            }
        }
        if (!found) {
            output.append(ch);
        }
        i++;
        if (i >= length) {
            finish = true;
        }
    }

    return output.toString();
  }

  /**
   * Validates sUri has a explicit ip6 authority.
   *
   * @param sUri String with normalized uri in the form http:// or https://
   * @return null if not explicit ip6 or host if explicit ip6 found
   */
  public static String extractIp6(String sUri) {
    if (sUri == null ||
        sUri.length() < 7 ||
        !sUri.startsWith("http")) {
        return null;
    }
    int beginFrom;
    if (sUri.charAt(4) == ':') {
        // http://
        beginFrom = 7;
    } else if (sUri.charAt(4) == 's') {
        // https://
        beginFrom = 8;
    } else {
        return null;
    }
    int firstSlash = sUri.indexOf('/', beginFrom);
    if (firstSlash == -1) firstSlash = sUri.length();
    String authority = sUri.substring(beginFrom, firstSlash);
    int lastColon = authority.lastIndexOf(':');
    if (lastColon == -1) {
        return null;
    } else {
        int openBracket = authority.lastIndexOf('[');
        if (openBracket == -1) {
            String untilColon = authority.substring(0, lastColon);
            boolean moreColons = untilColon.lastIndexOf(':') > -1;
            if (moreColons) {
                return authority;
            } else {
                return null;
            }
        } else {
            int closeBracket = authority.lastIndexOf(']');
            return authority.substring(openBracket, closeBracket + 1);
        }
    }
  }
}

