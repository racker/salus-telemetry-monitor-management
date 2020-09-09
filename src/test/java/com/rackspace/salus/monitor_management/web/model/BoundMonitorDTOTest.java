/*
 * Copyright 2020 Rackspace US, Inc.
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

package com.rackspace.salus.monitor_management.web.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.common.web.View;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ReflectionUtils;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@JsonTest
public class BoundMonitorDTOTest {
  /*
  Provide empty config to avoid failed attempt to setup unrelated component:
  org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'com.rackspace.salus.telemetry.web.TenantVerificationWebConfig'
   */
  @Configuration
  public static class TestConfig {}

  final PodamFactory podamFactory = new PodamFactoryImpl();

  /*
  Use Spring Boot provided ObjectMapper since default one fails with:
  com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot construct instance of `java.time.Duration` (no Creators, like default constructor, exist): cannot deserialize from Object value (no delegate- or property-based Creator)
  */
  @Autowired
  ObjectMapper objectMapper;

  @Test
  public void testFieldsCovered() throws JsonProcessingException {
    final BoundMonitor boundMonitor = podamFactory.manufacturePojo(BoundMonitor.class);

    final BoundMonitorDTO dto = new BoundMonitorDTO(boundMonitor);
    // manually fill summary since it gets populated by MonitorConversionService
    dto.setMonitorSummary(Map.of());

    // First verification approach is to check that all fields are populated with something.
    // This approach makes sure that the verification further down doesn't miss a new field.

    for (Field field : BoundMonitorDTO.class.getDeclaredFields()) {
      field.setAccessible(true);
      final Object value = ReflectionUtils.getField(field, dto);
      assertThat(field.getName(), value, notNullValue());
      if (value instanceof String) {
        assertThat(((String) value), not(is(emptyString())));
      }
    }

    // and next verification is to check populated with correct values

    assertThat(dto.getMonitorId(), equalTo(boundMonitor.getMonitor().getId()));
    assertThat(dto.getMonitorName(), equalTo(boundMonitor.getMonitor().getMonitorName()));
    assertThat(dto.getTenantId(), equalTo(boundMonitor.getTenantId()));
    assertThat(dto.getZoneName(), equalTo(boundMonitor.getZoneName()));
    assertThat(dto.getResourceId(), equalTo(boundMonitor.getResourceId()));
    assertThat(dto.getSelectorScope(), equalTo(boundMonitor.getMonitor().getSelectorScope()));
    assertThat(dto.getAgentType(), equalTo(boundMonitor.getMonitor().getAgentType()));
    assertThat(dto.getRenderedContent(), equalTo(boundMonitor.getRenderedContent()));
    assertThat(dto.getEnvoyId(), equalTo(boundMonitor.getEnvoyId()));
    assertThat(dto.getPollerResourceId(), equalTo(boundMonitor.getPollerResourceId()));
    assertThat(dto.getInterval(), equalTo(boundMonitor.getMonitor().getInterval()));

    BoundMonitorDTO convertedDto = objectMapper.readValue(
        objectMapper.writerWithView(View.Public.class).writeValueAsString(dto),
        BoundMonitorDTO.class);
    assertThat(convertedDto.getPollerResourceId(), nullValue());
    assertThat(convertedDto.getEnvoyId(), nullValue());

    convertedDto = objectMapper.readValue(
        objectMapper.writerWithView(View.Internal.class).writeValueAsString(dto),
        BoundMonitorDTO.class);
    assertThat(convertedDto.getPollerResourceId(), nullValue());
    assertThat(convertedDto.getEnvoyId(), equalTo(boundMonitor.getEnvoyId()));

    convertedDto = objectMapper.readValue(
        objectMapper.writerWithView(View.Admin.class).writeValueAsString(dto),
        BoundMonitorDTO.class);
    assertThat(convertedDto.getPollerResourceId(), equalTo(boundMonitor.getPollerResourceId()));
    assertThat(convertedDto.getEnvoyId(), equalTo(boundMonitor.getEnvoyId()));
  }
}