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

package com.rackspace.salus.monitor_management.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import java.lang.annotation.Annotation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {

  @Bean
  public Module jsr353Module() {
    // Register ability to handle Json Merge Patch conversions.
    return new JSR353Module();
  }

  /**
   * This can be used when (de)serialization of an object is required but the ignored properties
   * should be included.
   *
   * e.g. If some fields of an object are configured with @JsonInclude(Include.NON_NULL)
   * this can be used on the object mapper to ensure they are not ignored during conversion.
   *
   *    objectMapper.setAnnotationIntrospector(IGNORE_JSON_INCLUDE_ANNOTATIONS);
   *    objectMapper.convertValue(targetBean, JsonStructure.class);
   *
   */
  public static final JacksonAnnotationIntrospector IGNORE_JSON_INCLUDE_ANNOTATIONS = new JacksonAnnotationIntrospector() {
    @Override
    protected <A extends Annotation> A _findAnnotation(final Annotated annotated, final Class<A> annoClass) {
      if (!annotated.hasAnnotation(JsonInclude.class)) {
        return super._findAnnotation(annotated, annoClass);
      }
      return null;
    }
  };
}
