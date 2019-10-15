/*
 * Copyright 2019 Rackspace US, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rackspace.salus.monitor_management.web.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import javax.json.JsonMergePatch;
import javax.json.JsonPatch;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PatchHelper {

  public static final String JSON_MERGE_PATCH_TYPE = "application/json-patch+json";

  private final ObjectMapper objectMapper;
  private final Validator validator;

  @Autowired
  public PatchHelper(ObjectMapper objectMapper, Validator validator) {
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  /**
   * Performs a JSON Patch operation.
   *
   * @param patch      JSON Patch document
   * @param targetBean object that will be patched
   * @param beanClass  class of the object the will be patched
   * @param <T>
   * @return patched object
   */
  public <T> T patch(JsonPatch patch, T targetBean, Class<T> beanClass) {
    JsonStructure target = objectMapper.convertValue(targetBean, JsonStructure.class);
    JsonValue patched = applyPatch(patch, target);
    return convertAndValidate(patched, beanClass);
  }

  /**
   * Performs a JSON Merge Patch operation
   *
   * @param mergePatch JSON Merge Patch document
   * @param targetBean object that will be patched
   * @param beanClass  class of the object the will be patched
   * @param groups     validation groups for the operation
   * @param <T>
   * @return patched object
   */
  public <T> T mergePatch(JsonMergePatch mergePatch, T targetBean, Class<T> beanClass, Class<?>... groups) {
    JsonValue target = objectMapper.convertValue(targetBean, JsonValue.class);
    JsonValue patched = applyMergePatch(mergePatch, target);
    return convertAndValidate(patched, beanClass, groups);
  }

  private JsonValue applyPatch(JsonPatch patch, JsonStructure target) {
    try {
      return patch.apply(target);
    } catch (Exception e) {
      throw e;
    }
  }

  private JsonValue applyMergePatch(JsonMergePatch mergePatch, JsonValue target) {
    try {
      return mergePatch.apply(target);
    } catch (Exception e) {
      throw e;
    }
  }

  private <T> T convertAndValidate(JsonValue jsonValue, Class<T> beanClass, Class<?>... groups) {
    T bean = objectMapper.convertValue(jsonValue, beanClass);
    validate(bean, groups);
    return bean;
  }

  private <T> void validate(T bean, Class<?>... groups) {
    Set<ConstraintViolation<T>> violations = validator.validate(bean, groups);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
