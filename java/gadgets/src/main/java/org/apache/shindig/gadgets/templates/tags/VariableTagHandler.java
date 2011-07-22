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
package org.apache.shindig.gadgets.templates.tags;

import org.apache.shindig.gadgets.templates.TemplateProcessor;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Implement the osx:Variable tag
 */
public class VariableTagHandler extends AbstractTagHandler {

  static final String TAG_NAME = "Variable";

  @Inject
  public VariableTagHandler(@Named("shindig.template-rewrite.extension-tag-namespace") String namespace) {
    super(namespace, TAG_NAME);
  }

  public void process(Node result, Element tag, TemplateProcessor processor) {
    // Get the key.  Don't support EL (to match pipelining)
    String key = tag.getAttribute("key");
    if ("".equals(key)) {
      return;
    }
    
    // Get the value (with EL)
    Object value = getValueFromTag(tag, "value", processor, Object.class);
    
    if (processor.getTemplateContext().getMy() == null) {
      processor.getTemplateContext().setMy(Maps.<String, Object> newHashMap());
    }
    processor.getTemplateContext().getMy().put(key, value);
  }
}
