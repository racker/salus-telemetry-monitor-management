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

import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.repositories.TenantMetadataRepository;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@JsonTest
public class BoundMonitorDTOJsonTest {

  // A default timestamp to be used in test objects
  private static final String DEFAULT_TIMESTAMP = "1970-01-02T03:46:40Z";

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // IntelliJ has trouble resolving
  @Autowired
  private JacksonTester<BoundMonitorDTO> json;

  @MockBean
  TenantMetadataRepository tenantMetadataRepository;

  @Test
  public void testEmptyZone_nonNullEnvoy() throws IOException {

    final BoundMonitorDTO dto = new BoundMonitorDTO()
        .setZoneName("")
        .setMonitorId(UUID.fromString("00000000-0000-0000-0001-000000000000"))
        .setMonitorType(MonitorType.cpu)
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setAgentType(AgentType.TELEGRAF)
        .setRenderedContent("{}")
        .setEnvoyId("e-1")
        .setInterval(Duration.ofSeconds(30))
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

    assertThat(json.write(dto)).isEqualToJson("/BoundMonitorDTOJsonTest/testEmptyZone_nonNullEnvoy.json", JSONCompareMode.STRICT);

  }

  @Test
  public void testEmptyZone_nullEnvoy() throws IOException {

    final BoundMonitorDTO dto = new BoundMonitorDTO()
        .setZoneName("")
        .setMonitorId(UUID.fromString("00000000-0000-0000-0001-000000000000"))
        .setMonitorType(MonitorType.cpu)
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setAgentType(AgentType.TELEGRAF)
        .setRenderedContent("{}")
        .setEnvoyId(null)
        .setInterval(Duration.ofSeconds(30))
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

    assertThat(json.write(dto)).isEqualToJson("/BoundMonitorDTOJsonTest/testEmptyZone_nullEnvoy.json", JSONCompareMode.STRICT);

  }

  @Test
  public void testAllPopulated() throws IOException {

    final BoundMonitorDTO dto = new BoundMonitorDTO()
        .setZoneName("z-1")
        .setMonitorId(UUID.fromString("00000000-0000-0000-0001-000000000000"))
        .setMonitorType(MonitorType.cpu)
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setAgentType(AgentType.TELEGRAF)
        .setRenderedContent("{}")
        .setEnvoyId("e-1")
        .setInterval(Duration.ofSeconds(30))
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

    assertThat(json.write(dto)).isEqualToJson("/BoundMonitorDTOJsonTest/testAllPopulated.json", JSONCompareMode.STRICT);

  }
}