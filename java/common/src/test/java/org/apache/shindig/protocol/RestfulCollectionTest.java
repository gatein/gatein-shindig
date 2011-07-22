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
package org.apache.shindig.protocol;

import com.google.common.collect.Lists;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class RestfulCollectionTest extends Assert {
  @Test
  public void testBasicMethods() throws Exception {
    RestfulCollection<String> collection
        = new RestfulCollection<String>(Lists.<String>newArrayList());

    List<String> entry = Lists.newArrayList("banana");
    int startIndex = 5;
    int totalResults = 8675309;

    collection.setEntry(entry);
    collection.setStartIndex(startIndex);
    collection.setTotalResults(totalResults);

    assertEquals(entry, collection.getEntry());
    assertEquals(startIndex, collection.getStartIndex());
    assertEquals(totalResults, collection.getTotalResults());
  }

  @Test
  public void testConstructors() throws Exception {
    List<String> entry = Lists.newArrayList("banana", "who");
    RestfulCollection<String> collection = new RestfulCollection<String>(entry);

    assertEquals(entry, collection.getEntry());
    assertEquals(0, collection.getStartIndex());
    assertEquals(entry.size(), collection.getTotalResults());
    assertEquals(entry.size(), collection.getItemsPerPage());

    int startIndex = 9;
    int totalResults = 10;
    int resultsPerPage = 1;
    collection = new RestfulCollection<String>(entry, startIndex, totalResults, resultsPerPage);

    assertEquals(entry, collection.getEntry());
    assertEquals(startIndex, collection.getStartIndex());
    assertEquals(totalResults, collection.getTotalResults());
    assertEquals(resultsPerPage, collection.getItemsPerPage());
  }
}
