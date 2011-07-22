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
package org.apache.shindig.protocol.conversion;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.apache.shindig.common.uri.Uri;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Class to create a delegator (proxy) from an interface to a class.
 * It is used by the GadgetHandler to provide easy separation from interface
 * to actual implementation classes.
 * It uses Java reflection which require the usage of interfaces.
 * The validate function should be used in the test code to validate
 * that all API functions are implemented by the actual data, and it will
 * warn us if actual implementation change and break the API.
 * Delegation support composition, and will create a proxy for fields according
 * To table of classes to proxy.
 *
 * @since 2.0.0
 */
public class BeanDelegator {

  /** Indicate NULL value for a field (To overcome shortcome of immutable map) */
  public static final String NULL = "<NULL sentinel>";

  /** Gate a value to use NULL constant instead of null pointer */
  public static Object nullable(Object o) {
    return (o != null ? o : NULL);
  }

  private static final Map<String, Object> EMPTY_FIELDS = ImmutableMap.of();

  /** List of Classes that are considered primitives and are not proxied **/
  public static final ImmutableSet<Class<?>> PRIMITIVE_TYPE_CLASSES = ImmutableSet.of(
    String.class, Integer.class, Long.class, Boolean.class, Uri.class);

  /** Map from classes to proxy to the interface they are proxied by */
  private final Map<Class<?>, Class<?>> delegatedClasses;

  private final Map<Enum<?>, Enum<?>> enumConvertionMap;

  public BeanDelegator() {
    this(ImmutableMap.<Class<?>, Class<?>>of(),
         ImmutableMap.<Enum<?>, Enum<?>>of());
  }

  public BeanDelegator(Map<Class<?>, Class<?>> delegatedClasses,
                       Map<Enum<?>, Enum<?>> enumConvertionMap) {
    this.delegatedClasses = delegatedClasses;
    this.enumConvertionMap = enumConvertionMap;
  }

  /**
   * Create a proxy for the real object.
   * @param source item to proxy
   * @return proxied object according to map of classes to proxy
   */
  public Object createDelegator(Object source) {
    if (source == null || delegatedClasses == null) {
      return null;
    }

    // For enum, return the converted enum
    if (source instanceof Enum<?> && delegatedClasses.containsKey(source.getClass())) {
      return convertEnum((Enum<?>) source);
    }

    // Proxy each item in a map (map key is not proxied)
    if (source instanceof Map<?, ?>) {
      Map<?, ?> mapSource = (Map<?, ?>) source;
      if (!mapSource.isEmpty() && delegatedClasses.containsKey(
          mapSource.values().iterator().next().getClass())) {
        // Convert Map:
        ImmutableMap.Builder<Object, Object> mapBuilder = ImmutableMap.builder();
        for (Map.Entry<?, ?> entry : mapSource.entrySet()) {
          mapBuilder.put(entry.getKey(), createDelegator(entry.getValue()));
        }
        return mapBuilder.build();
      } else {
        return source;
      }
    }

    // Proxy each item in a list
    if (source instanceof List<?>) {
      List<?> listSource = (List<?>) source;
      if (!listSource.isEmpty() && delegatedClasses.containsKey(
        listSource.get(0).getClass())) {
        // Convert Map:
        ImmutableList.Builder<Object> listBuilder = ImmutableList.builder();
        for (Object entry : listSource) {
          listBuilder.add(createDelegator(entry));
        }
        return listBuilder.build();
      } else {
        return source;
      }
    }

    if (delegatedClasses.containsKey(source.getClass())) {
      Class<?> apiInterface = delegatedClasses.get(source.getClass());

      return createDelegator(source, apiInterface);
    }
    return source;
  }

  @SuppressWarnings("unchecked")
  public <T> T createDelegator(Object source, Class<T> apiInterface) {
    return createDelegator(source, apiInterface, EMPTY_FIELDS);
  }

  @SuppressWarnings("unchecked")
  public <T> T createDelegator(Object source, Class<T> apiInterface,
                               Map<String, Object> extraFields) {
    return (T) Proxy.newProxyInstance( apiInterface.getClassLoader(),
      new Class[] { apiInterface }, new DelegateInvocationHandler(source, extraFields));
  }

  public Enum<?> convertEnum(Enum<?> value) {
    if (enumConvertionMap.containsKey(value)) {
      return enumConvertionMap.get(value);
    }
    throw new UnsupportedOperationException("Unknown enum value " + value.toString());
  }

  protected class DelegateInvocationHandler implements InvocationHandler {
    /** Proxied object */
    private final Object source;
    /** Use the next values instead of proxying source */
    private final Map<String, Object> extraFields;

    public DelegateInvocationHandler(Object source) {
      this(source, null);
    }

    public DelegateInvocationHandler(Object source, Map<String, Object> extraFields) {
      Preconditions.checkNotNull(source);

      this.source = source;
      this.extraFields = (extraFields == null ? EMPTY_FIELDS : extraFields);
    }

    /**
     * Proxy the interface function to the source object
     * @throws UnsupportedOperationException if method is not supported by source
     */
    public Object invoke(Object proxy, Method method, Object[] args) {
      Class<?> sourceClass = source.getClass();
      // Return proxy fields if available
      if (!extraFields.isEmpty() && method.getName().startsWith("get")) {
        String field = method.getName().substring(3).toLowerCase();
        if (extraFields.containsKey(field)) {
          Object data = extraFields.get(field);
          return (data == NULL ? null : data);
        }
      }
      try {
        Method sourceMethod = sourceClass.getMethod(
            method.getName(), method.getParameterTypes());
        Object result = sourceMethod.invoke(source, args);
        return createDelegator(result);
      } catch (NoSuchMethodException e) {
        // Will throw unsupported method below
      } catch (IllegalArgumentException e) {
        // Will throw unsupported method below
      } catch (IllegalAccessException e) {
        // Will throw unsupported method below
      } catch (InvocationTargetException e) {
        // Will throw unsupported method below
      }
      throw new UnsupportedOperationException("Unsupported function: " + method.getName());
    }
  }

  /**
   * Validate all proxied classes to see that all required functions are implemented.
   * Throws exception if failed validation.
   * Note that it ignore the extra fields support.
   * @throws SecurityException
   * @throws NoSuchMethodException
   * @throws NoSuchFieldException
   */
  public void validate() throws SecurityException, NoSuchMethodException, NoSuchFieldException {
    for (Map.Entry<Class<?>, Class<?>> entry : delegatedClasses.entrySet()) {
      if (!entry.getKey().isEnum()) {
        validate(entry.getKey(), entry.getValue());
      }
    }
  }

  public void validate(Class<?> dataClass, Class<?> interfaceClass)
      throws SecurityException, NoSuchMethodException, NoSuchFieldException {
    for (Method method : interfaceClass.getMethods()) {
      Method dataMethod = dataClass.getMethod(method.getName(), method.getParameterTypes());
      if (dataMethod == null) {
        throw new NoSuchMethodException("Method " + method.getName()
            + " is not implemented by " + dataClass.getName());
      }
      if (!validateTypes(dataMethod.getGenericReturnType(), method.getGenericReturnType())) {
        throw new NoSuchMethodException("Method " + method.getName()
          + " has wrong return type by " + dataClass.getName());
      }
    }
  }

  private boolean validateTypes(Type dataType, Type interfaceType)
      throws NoSuchFieldException {

    // Handle Map and List parameterized types
    if (dataType instanceof ParameterizedType) {
      ParameterizedType dataParamType = (ParameterizedType) dataType;
      ParameterizedType interfaceParamType = (ParameterizedType) interfaceType;

      if (List.class.isAssignableFrom((Class<?>) dataParamType.getRawType()) &&
          List.class.isAssignableFrom((Class<?>) interfaceParamType.getRawType())) {

        dataType = dataParamType.getActualTypeArguments()[0];
        interfaceType = interfaceParamType.getActualTypeArguments()[0];
        return validateTypes(dataType, interfaceType);
      }
      if (Map.class.isAssignableFrom((Class<?>) dataParamType.getRawType()) &&
          Map.class.isAssignableFrom((Class<?>) interfaceParamType.getRawType())) {
        Type dataKeyType = dataParamType.getActualTypeArguments()[0];
        Type interfaceKeyType = interfaceParamType.getActualTypeArguments()[0];
        if (dataKeyType != interfaceKeyType || !PRIMITIVE_TYPE_CLASSES.contains(dataKeyType)) {
          return false;
        }
        dataType = dataParamType.getActualTypeArguments()[1];
        interfaceType = interfaceParamType.getActualTypeArguments()[1];
        return validateTypes(dataType, interfaceType);
      }
      // Only support Map and List generics
      return false;
    }

    // Primitive types
    if (dataType == interfaceType) {
      if (!PRIMITIVE_TYPE_CLASSES.contains(dataType) && !((Class<?>) dataType).isPrimitive()) {
        return false;
      }
      return true;
    }

    // Check all enum values are accounted for
    Class<?> dataClass = (Class<?>)dataType;
    if (dataClass.isEnum()) {
      for (Object f : dataClass.getEnumConstants()) {
        if (!enumConvertionMap.containsKey(f) ||
            enumConvertionMap.get(f).getClass() != interfaceType) {
          throw new NoSuchFieldException("Enum " + dataClass.getName()
            + " don't have mapping for value " + f.toString());
        }
      }
    }
    return (delegatedClasses.get(dataType) == interfaceType);
  }


  /**
   * Utility function to auto generate mapping between two enums that have same values (toString)
   * All values in the sourceEnum must have values in targetEnum,
   *  otherwise {@link RuntimeException} is thrown
   */
  public static Map<Enum<?>, Enum<?>> createDefaultEnumMap(
      Class<? extends Enum<?>> sourceEnum, Class<? extends Enum<?>> targetEnum) {
   Map<String, Enum<?>> values2Map = Maps.newHashMap();
   for (Object val2 : targetEnum.getEnumConstants()) {
     values2Map.put(val2.toString(), (Enum<?>) val2);
   }
   Enum<?>[] values1 = sourceEnum.getEnumConstants();
   ImmutableMap.Builder<Enum<?>, Enum<?>> mapBuilder = ImmutableMap.builder();
   for (Enum<?> val1 : sourceEnum.getEnumConstants()) {
     if (values2Map.containsKey(val1.toString())) {
       mapBuilder.put(val1, values2Map.get(val1.toString()));
     } else {
       throw new RuntimeException("Missing enum value " + val1.toString()
           + " for enum " + targetEnum.getName());
     }
   }
   return mapBuilder.build();
  }
}
