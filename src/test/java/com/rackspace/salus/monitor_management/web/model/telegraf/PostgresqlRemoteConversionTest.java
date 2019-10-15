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

package com.rackspace.salus.monitor_management.web.model.telegraf;

import static com.rackspace.salus.monitor_management.web.model.telegraf.ConversionHelpers.assertCommonRemote;
import static com.rackspace.salus.monitor_management.web.model.telegraf.ConversionHelpers.createMonitor;
import static com.rackspace.salus.test.JsonTestUtils.readContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@JsonTest
@Import({MonitorConversionService.class, MetadataUtils.class})
public class PostgresqlRemoteConversionTest {
  @Configuration
  public static class TestConfig { }

  @MockBean
  PatchHelper patchHelper;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  MonitorRepository monitorRepository;

  @Autowired
  MonitorConversionService conversionService;

  @Autowired
  MetadataUtils metadataUtils;

  @Test
  public void convertToOutput_postgresql_remote() throws IOException {
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_postgresql.json");

    Monitor monitor = createMonitor(content, "convertToOutput", AgentType.TELEGRAF,
        ConfigSelectorScope.REMOTE
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final PostgresqlRemote postgresqlPlugin = assertCommonRemote(result, monitor, PostgresqlRemote.class, "convertToOutput");

    List<String> l = List.of("1","2");
    List<String> l2 = List.of("3","4");
    assertThat(postgresqlPlugin.getAddress()).isEqualTo("host=localhost user=postgres sslmode=disable");
    assertThat(postgresqlPlugin.getOutputaddress()).isEqualTo("db1");
    assertThat(postgresqlPlugin.getMaxLifetime()).isEqualTo("0s");
    assertThat(postgresqlPlugin.getIgnoredDatabases()).isEqualTo(l);
    assertThat(postgresqlPlugin.getDatabases()).isEqualTo(l2);
  }

  @Test
  public void convertToOutput_postgresql_remote_defaults() {
    final String content = "{\"type\": \"postgresql\"}";

    Monitor monitor = createMonitor(content, "convertToOutput_defaults", AgentType.TELEGRAF,
        ConfigSelectorScope.REMOTE
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final PostgresqlRemote postgresqlPlugin = assertCommonRemote(result, monitor, PostgresqlRemote.class, "convertToOutput_defaults");
    assertThat(postgresqlPlugin.getAddress()).isEqualTo(null);
    assertThat(postgresqlPlugin.getOutputaddress()).isEqualTo(null);
    assertThat(postgresqlPlugin.getMaxLifetime()).isEqualTo(null);
    assertThat(postgresqlPlugin.getIgnoredDatabases()).isEqualTo(null);
    assertThat(postgresqlPlugin.getDatabases()).isEqualTo(null);

  }

  @Test
  public void convertFromInput_postgresql_remote() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput");

    List<String> l = List.of("1","2");
    List<String> l2 = List.of("3","4");
    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    final PostgresqlRemote plugin = new PostgresqlRemote();
    plugin.setAddress("host=localhost user=postgres sslmode=disable");
    plugin.setOutputaddress("db1");
    plugin.setMaxLifetime("0s");
    plugin.setIgnoredDatabases(l);
    plugin.setDatabases(l2);
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setName("name-a")
        .setLabelSelector(labels)
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10),
        null,
        input);

    assertThat(result).isNotNull();
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(result.getMonitorName()).isEqualTo("name-a");
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.REMOTE);
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_postgresql.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

}
