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
package org.apache.shindig.gadgets.render;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

/**
 * Simple storage for holding the various services offered by containers.
 *
 * This storage is keyed by container, and within each container, the services are stored, keyed by,
 * the endpoint url.
 *
 * Here is a json structure that shows how data is stored:
 *
 * { container1 : { "http://.../endpoint1" : ["system.listMethods", "people.get", "people.create",
 * "people.delete"], ... }, "http://.../endpoint2" : { "system.listMethods", "cache.invalidate"],
 * ... } }, container 2 : ..... }
 */
@Singleton
public class DefaultRpcServiceLookup implements RpcServiceLookup {

  private final ConcurrentMap<String, Multimap<String, String>> containerServices;

  private final DefaultServiceFetcher fetcher;

  /**
   * @param fetcher  RpcServiceFetcher to retrieve services available from endpoints
   * @param duration in seconds service definitions should remain in the cache
   */
  @Inject
  public DefaultRpcServiceLookup(DefaultServiceFetcher fetcher,
      @Named("org.apache.shindig.serviceExpirationDurationMinutes")Long duration) {
    containerServices = new MapMaker().expiration(duration * 60, TimeUnit.SECONDS).makeMap();
    this.fetcher = fetcher;
  }

  /**
   * @param container Syndicator param identifying the container for whom we want services
   * @param host      Host for which gadget is being rendered, used to do substitution in endpoints
   * @return Map of Services, by endpoint for the given container.
   */
  public Multimap<String, String> getServicesFor(String container, String host) {
    // Support empty container or host by providing empty services:
    if (container == null || container.length() == 0 || host == null) {
      return ImmutableMultimap.<String, String>builder().build();
    }

    Multimap<String, String> foundServices = containerServices.get(container);
    if (foundServices == null) {
      foundServices = fetcher.getServicesForContainer(container, host);
      if (foundServices != null) {
        setServicesFor(container, foundServices);
      }
    }
    if (foundServices == null) {
      foundServices = ImmutableMultimap.<String, String>builder().build();
    }
    return foundServices;
  }

  /**
   * Setup the services for a given container.
   *
   * @param container     The param identifying this container.
   * @param foundServices Map of services, keyed by endpoint.
   */
  void setServicesFor(String container, Multimap<String, String> foundServices) {
    containerServices.put(container, foundServices);
  }

}
