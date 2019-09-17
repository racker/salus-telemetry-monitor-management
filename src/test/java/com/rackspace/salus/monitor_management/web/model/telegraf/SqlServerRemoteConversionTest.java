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
public class SqlServerRemoteConversionTest {
  @Configuration
  public static class TestConfig { }

  @Autowired
  MonitorConversionService conversionService;

  @Test
  public void convertToOutput_sql_server() throws IOException {
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_sql_server.json");

    Monitor monitor = createMonitor(content, "convertToOutput", AgentType.TELEGRAF,
        ConfigSelectorScope.REMOTE
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final SqlServerRemote sqlServerPlugin = assertCommonRemote(result, monitor, SqlServerRemote.class, "convertToOutput");

    List<String> l = List.of("1","2");
    List<String> l2 = List.of("3","4");
    assertThat(sqlServerPlugin.getServers()).isEqualTo(l);
    assertThat(sqlServerPlugin.getQueryVersion()).isEqualTo(2);
    assertThat(sqlServerPlugin.isAzuredb()).isTrue();
    assertThat(sqlServerPlugin.getExcludeQuery()).isEqualTo(l2);
  }

  @Test
  public void convertToOutput_sql_server_defaults() {
    final String content = "{\"type\": \"sqlserver\"}";

    Monitor monitor = createMonitor(content, "convertToOutput_defaults", AgentType.TELEGRAF,
        ConfigSelectorScope.REMOTE
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final SqlServerRemote sqlServerPlugin = assertCommonRemote(result, monitor, SqlServerRemote.class, "convertToOutput_defaults");
    assertThat(sqlServerPlugin.getServers()).isEqualTo(null);
    assertThat(sqlServerPlugin.getQueryVersion()).isEqualTo(2);
    assertThat(sqlServerPlugin.isAzuredb()).isFalse();
    assertThat(sqlServerPlugin.getExcludeQuery()).isEqualTo(null);

  }

  @Test
  public void convertFromInput_sql_server() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput");

    List<String> l = List.of("1","2");
    List<String> l2 = List.of("3","4");
    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    final SqlServerRemote plugin = new SqlServerRemote();
    plugin.setServers(l);
    plugin.setQueryVersion(2);
    plugin.setAzuredb(true);
    plugin.setExcludeQuery(l2);
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
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_sql_server.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

}
