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
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.monitor_management.entities.Zone;
import org.junit.Test;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

public class ZoneDTOTest {
  final PodamFactory podamFactory = new PodamFactoryImpl();

  @Test
  public void testFieldsCovered() {
    final Zone zone = podamFactory.manufacturePojo(Zone.class);

    final ZoneDTO dto = zone.toDTO();

    assertThat(dto.getName(), notNullValue());
    assertThat(dto.getPollerTimeout(), notNullValue());
    assertThat(dto.getProvider(), notNullValue());
    assertThat(dto.getProviderRegion(), notNullValue());
    assertThat(dto.getSourceIpAddresses(), notNullValue());
    assertThat(dto.getState(), notNullValue());
    assertThat(dto.isPublic(), notNullValue());

    assertThat(dto.getName(), equalTo(zone.getName()));
    assertThat(dto.getPollerTimeout(), equalTo(zone.getPollerTimeout().getSeconds()));
    assertThat(dto.getProvider(), equalTo(zone.getProvider()));
    assertThat(dto.getProviderRegion(), equalTo(zone.getProviderRegion()));
    assertThat(dto.getSourceIpAddresses(), equalTo(zone.getSourceIpAddresses()));
    assertThat(dto.getState(), equalTo(zone.getState()));
    assertThat(dto.isPublic(), equalTo(zone.isPublic()));
  }
}