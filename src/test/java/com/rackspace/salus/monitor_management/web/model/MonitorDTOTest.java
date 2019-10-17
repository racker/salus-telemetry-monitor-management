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

package com.rackspace.salus.monitor_management.web.model;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.telemetry.entities.Monitor;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ReflectionUtils;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@JsonTest
public class MonitorDTOTest {

  private PodamFactory podamFactory = new PodamFactoryImpl();

  @Test
  public void testFieldsCoveredWithResourceId() {
    final Monitor monitor = podamFactory.manufacturePojo(Monitor.class);

    final MonitorDTO dto = new MonitorDTO(monitor);

    testFields(monitor, dto, true);
  }

  @Test
  public void testFieldsCoveredWithNullResourceId() {
    final Monitor monitor = podamFactory.manufacturePojo(Monitor.class)
        .setResourceId(null);

    final MonitorDTO dto = new MonitorDTO(monitor);

    testFields(monitor, dto, false);
  }

  private void testFields(Monitor monitor, MonitorDTO dto, boolean resourceIdSet){
    // First verification approach is to check that all fields are populated with something.
    // This approach makes sure that the verification further down doesn't miss a new field.

    for (Field field : MonitorDTO.class.getDeclaredFields()) {
      field.setAccessible(true);
      final Object value = ReflectionUtils.getField(field, dto);
      if (resourceIdSet && field.getName().equals("labelSelector")) {
        assertThat(value, nullValue());
      } else if (!resourceIdSet && field.getName().equals("resourceId")) {
        assertThat(value, nullValue());
      } else {
        assertThat(value, notNullValue());
      }
      if (value instanceof String) {
        assertThat(((String) value), not(isEmptyString()));
      }
    }

    // and next verification is to check populated with correct values

    // when the resourceId is set the label selector should be null
    if (resourceIdSet) {
      assertThat(dto.getLabelSelector(), nullValue());
    } else {
      assertThat(dto.getLabelSelector(), equalTo(monitor.getLabelSelector()));
    }

    assertThat(dto.getId(), equalTo(monitor.getId()));
    assertThat(dto.getResourceId(), equalTo(monitor.getResourceId()));
    assertThat(dto.getMonitorName(), equalTo(monitor.getMonitorName()));
    assertThat(dto.getLabelSelectorMethod(), equalTo(monitor.getLabelSelectorMethod()));
    assertThat(dto.getTenantId(), equalTo(monitor.getTenantId()));
    assertThat(dto.getContent(), equalTo(monitor.getContent()));
    assertThat(dto.getAgentType(), equalTo(monitor.getAgentType()));
    assertThat(dto.getSelectorScope(), equalTo(monitor.getSelectorScope()));
    assertThat(dto.getZones(), equalTo(monitor.getZones()));
    assertThat(dto.getCreatedTimestamp(), equalTo(monitor.getCreatedTimestamp().toString()));
    assertThat(dto.getUpdatedTimestamp(), equalTo(monitor.getUpdatedTimestamp().toString()));
  }
}