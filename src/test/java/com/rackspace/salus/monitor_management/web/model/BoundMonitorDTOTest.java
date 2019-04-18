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
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import org.junit.Test;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

public class BoundMonitorDTOTest {
  final PodamFactory podamFactory = new PodamFactoryImpl();

  /**
   * Tests that we can do a round-trip conversion using {@link ObjectMapper#convertValue(Object, Class)}
   */
  @Test
  public void testFieldsCovered() {
    final ObjectMapper objectMapper = new ObjectMapper();

    final BoundMonitor boundMonitor = podamFactory.manufacturePojo(BoundMonitor.class);

    final BoundMonitorDTO dto = objectMapper
        .convertValue(boundMonitor, BoundMonitorDTO.class);

    final BoundMonitor result = objectMapper.convertValue(dto, BoundMonitor.class);

    assertThat(result, equalTo(boundMonitor));
  }
}