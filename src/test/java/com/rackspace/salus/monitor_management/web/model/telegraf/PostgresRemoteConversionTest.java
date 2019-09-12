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

import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@JsonTest
@Import({MonitorConversionService.class})
public class PostgresRemoteConversionTest {
  @Configuration
  public static class TestConfig { }

  @Autowired
  MonitorConversionService conversionService;

  @Test
  public void convertToOutput_postgres_remote() throws IOException {
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_postgres.json");

    Monitor monitor = createMonitor(content, "convertToOutput", AgentType.TELEGRAF,
        ConfigSelectorScope.REMOTE
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final PostgresRemote postgresPlugin = assertCommonRemote(result, monitor, PostgresRemote.class, "convertToOutput");

    List<String> l = new ArrayList<>();
    l.add("1");
    l.add("2");
    List<String> l2 = new ArrayList<>();
    l2.add("3");
    l2.add("4");
    assertThat(postgresPlugin.getAddress()).isEqualTo("host=localhost user=postgres sslmode=disable");
    assertThat(postgresPlugin.getOutputaddress()).isEqualTo("db1");
    assertThat(postgresPlugin.getMaxLifetime()).isEqualTo("0s");
    assertThat(postgresPlugin.getIgnoredDatabases()).isEqualTo(l);
    assertThat(postgresPlugin.getDatabases()).isEqualTo(l2);
  }

  @Test
  public void convertToOutput_postgres_remote_defaults() {
    final String content = "{\"type\": \"postgres\"}";

    Monitor monitor = createMonitor(content, "convertToOutput_defaults", AgentType.TELEGRAF,
        ConfigSelectorScope.REMOTE
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final PostgresRemote postgresPlugin = assertCommonRemote(result, monitor, PostgresRemote.class, "convertToOutput_defaults");
    assertThat(postgresPlugin.getAddress()).isEqualTo(null);
    assertThat(postgresPlugin.getOutputaddress()).isEqualTo(null);
    assertThat(postgresPlugin.getMaxLifetime()).isEqualTo(null);
    assertThat(postgresPlugin.getIgnoredDatabases()).isEqualTo(null);
    assertThat(postgresPlugin.getDatabases()).isEqualTo(null);

  }

  @Test
  public void convertFromInput_postgres_remote() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput");

    List<String> l = new ArrayList<>();
    l.add("1");
    l.add("2");
    List<String> l2 = new ArrayList<>();
    l2.add("3");
    l2.add("4");
    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    final PostgresRemote plugin = new PostgresRemote();
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
    final MonitorCU result = conversionService.convertFromInput(input);

    assertThat(result).isNotNull();
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(result.getMonitorName()).isEqualTo("name-a");
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.REMOTE);
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_postgres.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

}
