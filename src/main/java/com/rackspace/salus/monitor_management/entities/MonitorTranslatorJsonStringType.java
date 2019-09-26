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

package com.rackspace.salus.monitor_management.entities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.web.model.translators.MonitorTranslator;
import java.io.IOException;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;

/**
 * Since the translator specs are persisted as a JSON <code>varchar</code> column,
 * but the code using the entity always needs to work with the specific instance of {@link MonitorTranslator}
 * this custom Hibernate type simplifies the conversion into and out of the JSON encoding of those instances.
 * <p>
 * <em>NOTE</em> the hibernate-types library wasn't a viable solution since the SQL type
 * declared by the JSON string types is hardcoded as "OTHER".
 * </p>
 */
public class MonitorTranslatorJsonStringType extends
    AbstractSingleColumnStandardBasicType<MonitorTranslator> {

  public MonitorTranslatorJsonStringType() {
    super(VarcharTypeDescriptor.INSTANCE, MonitorTranslatorJsonStringTypeDescriptor.INSTANCE);
  }

  @Override
  public String getName() {
    return "monitorTypeJsonString";
  }

  private static class MonitorTranslatorJsonStringTypeDescriptor extends
      AbstractTypeDescriptor<MonitorTranslator> {

    static final JavaTypeDescriptor<MonitorTranslator> INSTANCE =
        new MonitorTranslatorJsonStringTypeDescriptor();

    // It's a bummer we can't use Spring Boot's ObjectMapper, but Hibernate's type registry is
    // bootstrapped independently from the Spring app context
    final ObjectMapper objectMapper = new ObjectMapper();

    MonitorTranslatorJsonStringTypeDescriptor() {
      super(MonitorTranslator.class);
    }

    @Override
    public MonitorTranslator fromString(String value) {
      try {
        return objectMapper.readValue(value, MonitorTranslator.class);
      } catch (IOException e) {
        throw new IllegalArgumentException("Failed to deserialize MonitorTranslator", e);
      }
    }

    @Override
    public <X> MonitorTranslator wrap(X value, WrapperOptions wrapperOptions) {
      if (value instanceof String) {
        return fromString(((String) value));
      }

      throw unknownWrap(value.getClass());
    }

    @Override
    public <X> X unwrap(MonitorTranslator monitorTranslator, Class<X> aClass,
                        WrapperOptions wrapperOptions) {
      try {
        //noinspection unchecked
        return (X) objectMapper.writeValueAsString(monitorTranslator);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Failed to serialize MonitorTranslator", e);
      }
    }
  }
}
