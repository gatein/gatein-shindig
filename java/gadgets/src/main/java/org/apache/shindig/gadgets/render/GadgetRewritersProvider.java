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

import java.util.Set;

import org.apache.shindig.gadgets.GadgetContext;
import org.apache.shindig.gadgets.rewrite.GadgetRewriter;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Class to provide list of rewriters according to gadget request.
 * Provide different list of rewriters for html accelerate request
 *
 * @since 2.0.0
 */
public class GadgetRewritersProvider {
  private final Set<GadgetRewriter> renderRewriters;

  @Inject
  public GadgetRewritersProvider(@Named("shindig.rewriters.gadget") Set<GadgetRewriter> renderRewriters) {
    this.renderRewriters = renderRewriters;
  }

  public Set<GadgetRewriter> getRewriters(GadgetContext context) {
    return renderRewriters;
  }
}
