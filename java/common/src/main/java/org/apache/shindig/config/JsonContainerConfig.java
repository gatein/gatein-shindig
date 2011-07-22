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

package org.apache.shindig.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.apache.commons.lang.StringUtils;
import org.apache.shindig.common.JsonSerializer;
import org.apache.shindig.common.util.ResourceLoader;
import org.apache.shindig.expressions.Expressions;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

// Temporary replacement of javax.annotation.Nullable
import org.apache.shindig.common.Nullable;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ValueExpression;

/**
 * Represents a container configuration using JSON notation.
 *
 * See config/container.js for an example configuration.
 *
 * We use a cascading model, so you only have to specify attributes in
 * your config that you actually want to change.
 *
 * String values may use expressions. The variable context defaults to the 'current' container,
 * but parent values may be accessed through the special "parent" property.
 */
@Singleton
public class JsonContainerConfig extends AbstractContainerConfig {
  private static final Logger LOG = Logger.getLogger(JsonContainerConfig.class.getName());
  public static final char FILE_SEPARATOR = ',';
  public static final String PARENT_KEY = "parent";
  // TODO: Rename this to simply "container", gadgets.container is unnecessary.
  public static final String CONTAINER_KEY = "gadgets.container";
  public static final String SERVER_PORT = "SERVER_PORT";
  public static final String SERVER_HOST = "SERVER_HOST";

  private final Map<String, Map<String, Object>> config;
  private final Expressions expressions;

  private static final Pattern CRLF_PATTERN = Pattern.compile("[\r\n]+");

  private final String host;
  private final String port;

  // Used by tests
  public JsonContainerConfig(String containers, Expressions expressions) throws ContainerConfigException {
    this(containers, "localhost", "8080", expressions);
  }
  /**
   * Creates a new configuration from files.
   * @throws ContainerConfigException
   */
  @Inject
  public JsonContainerConfig(@Named("shindig.containers.default") String containers,
                             @Nullable @Named("shindig.host") String host,
                             @Nullable @Named("shindig.port") String port,
                             Expressions expressions)
      throws ContainerConfigException {
    this.expressions = expressions;
    this.host = host;
    this.port = port;

    JSONObject configJson = loadContainers(containers);
    config = createContainers(configJson);
    init();
  }

  /**
   * Creates a new configuration from a JSON Object, for use in testing.
   */
  public JsonContainerConfig(JSONObject json, Expressions expressions) {
    this.expressions = expressions;
    host = null;
    port = null;
    config = createContainers(json);
    init();
  }

  /**
   * Initializes the configuration.  Called during construction.
   */
  protected void init() {
    for (Map.Entry<String, Map<String, Object>> configEntry : config.entrySet()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> value = (Map<String, Object>) evaluateAll(configEntry.getValue());
      configEntry.setValue(value);
    }
  }

  @Override
  public Collection<String> getContainers() {
    return Collections.unmodifiableSet(config.keySet());
  }

  @Override
  public Map<String, Object> getProperties(String container) {
    return config.get(container);
  }

  @Override
  public Object getProperty(String container, String property) {
    if (property.startsWith("${")) {
      // An expression!
      try {
        ValueExpression expression = expressions.parse(property, Object.class);
        return expression.getValue(createExpressionContext(container));
      } catch (ELException e) {
        return null;
      }
    }

    Map<String, Object> containerData = config.get(container);
    if (containerData == null) {
      return null;
    }
    return containerData.get(property);
  }

  /**
   * Initialize each container's configuration.
   */
  private Map<String, Map<String, Object>> createContainers(JSONObject json) {
    Map<String, Map<String, Object>> map = Maps.newHashMap();
    for (String container : JSONObject.getNames(json)) {
      ELContext context = createExpressionContext(container);
      map.put(container, jsonToMap(json.optJSONObject(container), expressions, context));
    }

    return map;
  }

  /**
   * Make Expressions available to subclasses so they can create ELContexts
   */
  protected Expressions getExpressions() {
    return expressions;
  }

  /**
   * Protected to allow overriding.
   */
  protected ELContext createExpressionContext(String container) {
    return getExpressions().newELContext(new ContainerConfigELResolver(this, container));
  }

  /**
   * Convert a JSON value to a configuration value.
   */
  private static Object jsonToConfig(Object json, Expressions expressions, ELContext context) {
    if (JSONObject.NULL.equals(json)) {
      return null;
    } else if (json instanceof CharSequence) {
      return new DynamicConfigProperty(json.toString(), expressions, context);
    } else if (json instanceof JSONArray) {
      JSONArray jsonArray = (JSONArray) json;
      List<Object> values = new ArrayList<Object>(jsonArray.length());
      for (int i = 0, j = jsonArray.length(); i < j; ++i) {
        values.add(jsonToConfig(jsonArray.opt(i), expressions, context));
      }
      return Collections.unmodifiableList(values);
    } else if (json instanceof JSONObject) {
      return jsonToMap((JSONObject) json, expressions, context);
    }

    // A (boxed) primitive.
    return json;
  }

  private static Map<String, Object> jsonToMap(JSONObject json, Expressions expressions, ELContext context) {
    Map<String, Object> values = new HashMap<String, Object>(json.length(), 1);
    for (String key : JSONObject.getNames(json)) {
      Object val = jsonToConfig(json.opt(key), expressions, context);
      if (val != null) {
        values.put(key, val);
      }
    }
    return Collections.unmodifiableMap(values);
  }

  /**
   * Loads containers from directories recursively.
   *
   * Only files with a .js or .json extension will be loaded.
   *
   * @param files The files to examine.
   * @throws ContainerConfigException
   */
  private void loadFiles(File[] files, JSONObject all) throws ContainerConfigException {
    for (File file : files) {
      try {
        if (file == null) continue;
        LOG.info("Reading container config: " + file.getName());
        if (file.isDirectory()) {
          loadFiles(file.listFiles(), all);
        } else if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".js") ||
                   file.getName().toLowerCase(Locale.ENGLISH).endsWith(".json")) {
          if (!file.exists()) {
            throw new ContainerConfigException(
                "The file '" + file.getAbsolutePath() + "' doesn't exist.");
          }
          loadFromString(ResourceLoader.getContent(file), all);
        } else {
          if (LOG.isLoggable(Level.FINEST))
            LOG.finest(file.getAbsolutePath() + " doesn't seem to be a JS or JSON file.");
        }
      } catch (IOException e) {
        throw new ContainerConfigException("The file '" + file.getAbsolutePath() + "' has errors", e);
      }
    }
  }

  /**
   * Loads resources recursively.
   * @param files The base paths to look for container.xml
   * @throws ContainerConfigException
   */
  private void loadResources(String[] files, JSONObject all)  throws ContainerConfigException {
    try {
      for (String entry : files) {
        LOG.info("Reading container config: " + entry);
        String content = ResourceLoader.getContent(entry);
        if (content == null || content.length() == 0)
          throw new IOException("The file " + entry + "is empty");
        loadFromString(content, all);
      }
    } catch (IOException e) {
      throw new ContainerConfigException(e);
    }
  }

  /**
   * Merges two JSON objects together (recursively), with values from "merge"
   * replacing values in "base" to produce a new object.
   *
   * @param base The base object that values will be replaced into.
   * @param merge The object to merge values from.
   *
   * @throws org.json.JSONException if the two objects can't be merged for some reason.
   */
  private JSONObject mergeObjects(JSONObject base, JSONObject merge)
      throws JSONException {
    // Clone the initial object (JSONObject doesn't support "clone").

    JSONObject clone = new JSONObject(base, JSONObject.getNames(base));
    // Walk parameter list for the merged object and merge recursively.
    String[] fields = JSONObject.getNames(merge);
    for (String field : fields) {
      Object existing = clone.opt(field);
      Object update = merge.get(field);
      if (JSONObject.NULL.equals(existing) || JSONObject.NULL.equals(update)) {
        // It's new custom config, not referenced in the prototype, or
        // it's removing a pre-configured value.
        clone.put(field, update);
      } else {
        // Merge if object type is JSONObject.
        if (update instanceof JSONObject &&
            existing instanceof JSONObject) {
          clone.put(field, mergeObjects((JSONObject)existing,
                                        (JSONObject)update));
        } else {
          // Otherwise we just overwrite it.
          clone.put(field, update);
        }
      }
    }
    return clone;
  }

  /**
   * Recursively merge values from parent objects in the prototype chain.
   *
   * @return The object merged with all parents.
   *
   * @throws ContainerConfigException If there is an invalid parent parameter
   *    in the prototype chain.
   */
  private JSONObject mergeParents(String container, JSONObject all)
      throws ContainerConfigException, JSONException {
    JSONObject base = all.getJSONObject(container);
    if (DEFAULT_CONTAINER.equals(container)) {
      return base;
    }

    String parent = base.optString(PARENT_KEY, DEFAULT_CONTAINER);
    if (!all.has(parent)) {
      throw new ContainerConfigException(
          "Unable to locate parent '" + parent + "' required by "
          + base.getString(CONTAINER_KEY));
    }
    return mergeObjects(mergeParents(parent, all), base);
  }

  /**
   * Processes a container file.
   *
   * @param json
   * @throws ContainerConfigException
   */
  protected void loadFromString(String json, JSONObject all) throws ContainerConfigException {
    try {
      JSONObject contents = new JSONObject(json);
      JSONArray containers = contents.getJSONArray(CONTAINER_KEY);

      for (int i = 0, j = containers.length(); i < j; ++i) {
        // Copy the default object and produce a new one.
        String container = containers.getString(i);
        all.put(container, contents);
      }
    } catch (JSONException e) {
      LOG.warning("Trouble parsing " + json);
      throw new ContainerConfigException("Trouble parsing " + json, e);
    }
  }

  /**
   * Loads containers from the specified resource. Follows the same rules
   * as {@code JsFeatureLoader.loadFeatures} for locating resources.
   *
   * @param path
   * @throws ContainerConfigException
   */
  private JSONObject loadContainers(String path) throws ContainerConfigException {
    JSONObject all = new JSONObject();
    try {
      for (String location : StringUtils.split(path, FILE_SEPARATOR)) {
        if (location.startsWith("res://")) {
          location = location.substring(6);
          LOG.info("Loading resources from: " + location);
          if (path.endsWith(".txt")) {
            loadResources(CRLF_PATTERN.split(ResourceLoader.getContent(location)), all);
          } else {
            loadResources(new String[]{location}, all);
          }
        } else {
          LOG.info("Loading files from: " + location);
          File file = new File(location);
          loadFiles(new File[]{file}, all);
        }
      }

      // Add {@code SERVER_HOST} and {@code SERVER_PORT} environment to default
      // container.
      try {
        all.getJSONObject(DEFAULT_CONTAINER).put(SERVER_PORT, port);
        all.getJSONObject(DEFAULT_CONTAINER).put(SERVER_HOST, host);
      } catch (JSONException e) {
        // ignore
      }

      // Now that all containers are loaded, we go back through them and merge
      // recursively. This is done at startup to simplify lookups.
      for (String container : JSONObject.getNames(all)) {
        all.put(container, mergeParents(container, all));
      }

      return all;
    } catch (IOException e) {
      throw new ContainerConfigException(e);
    } catch (JSONException e) {
      throw new ContainerConfigException(e);
    }
  }

  @Override
  public String toString() {
    return JsonSerializer.serialize(config);
  }

  private Object evaluateAll(Object value) {
    if (value instanceof CharSequence) {
      return value.toString();
    } else if (value instanceof Map<?, ?>) {
      Map<?, ?> mapValue = (Map<?, ?>) value;
      ImmutableMap.Builder<Object, Object> newMap = ImmutableMap.builder();
      for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
        newMap.put(entry.getKey(), evaluateAll(entry.getValue()));
      }

      return newMap.build();
    } else if (value instanceof List<?>) {
      ImmutableList.Builder<Object> newList = ImmutableList.builder();
      for (Object entry : (List<?>) value) {
        newList.add(evaluateAll(entry));
      }

      return newList.build();
    } else {
      return value;
    }
  }
}
