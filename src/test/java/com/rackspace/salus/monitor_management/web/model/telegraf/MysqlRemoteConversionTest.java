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
public class MysqlRemoteConversionTest {
  @Configuration
  public static class TestConfig { }

  @Autowired
  MonitorConversionService conversionService;

  @Test
  public void convertToOutput_mysqlremote() throws IOException {
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_mysql.json");

    Monitor monitor = createMonitor(content, "convertToOutput", AgentType.TELEGRAF,
        ConfigSelectorScope.REMOTE
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final MysqlRemote mysqlPlugin = assertCommonRemote(result, monitor, MysqlRemote.class, "convertToOutput");

    List<String> l = List.of("1","2");
    assertThat(mysqlPlugin.getServers()).isEqualTo(l);
    assertThat(mysqlPlugin.getPerfEventsStatementsDigestTextLimit()).isEqualTo(1);
    assertThat(mysqlPlugin.getPerfEventsStatementsLimit()).isEqualTo(2);
    assertThat(mysqlPlugin.getPerfEventsStatementsTimeLimit()).isEqualTo(3);
    assertThat(mysqlPlugin.getTableSchemaDatabases()).isEqualTo(l);
    assertThat(mysqlPlugin.isGatherProcessList()).isFalse();
    assertThat(mysqlPlugin.isGatherUserStatistics()).isTrue();
    assertThat(mysqlPlugin.isGatherInfoSchemaAutoInc()).isFalse();
    assertThat(mysqlPlugin.isGatherInnodbMetrics()).isTrue();
    assertThat(mysqlPlugin.isGatherSlaveStatus()).isFalse();
    assertThat(mysqlPlugin.isGatherBinaryLogs()).isTrue();
    assertThat(mysqlPlugin.isGatherTableIoWaits()).isFalse();
    assertThat(mysqlPlugin.isGatherTableLockWaits()).isTrue();
    assertThat(mysqlPlugin.isGatherIndexIoWaits()).isFalse();
    assertThat(mysqlPlugin.isGatherEventWaits()).isTrue();
    assertThat(mysqlPlugin.isGatherTableSchema()).isFalse();
    assertThat(mysqlPlugin.isGatherFileEventsStats()).isTrue();
    assertThat(mysqlPlugin.isGatherPerfEventsStatements()).isFalse();
    assertThat(mysqlPlugin.getIntervalSlow()).isEqualTo("3s");
    assertThat(mysqlPlugin.getMetricVersion()).isEqualTo(2);
    assertThat(mysqlPlugin.getTlsCa()).isEqualTo("tlsCa");
    assertThat(mysqlPlugin.getTlsCert()).isEqualTo("tlsCert");
    assertThat(mysqlPlugin.getTlsKey()).isEqualTo("tlsKey");
  }

  @Test
  public void convertToOutput_mysqlremote_defaults() {
    final String content = "{\"type\": \"mysql\"}";

    Monitor monitor = createMonitor(content, "convertToOutput_defaults", AgentType.TELEGRAF,
        ConfigSelectorScope.REMOTE
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final MysqlRemote mysqlPlugin = assertCommonRemote(result, monitor, MysqlRemote.class, "convertToOutput_defaults");
    assertThat(mysqlPlugin.getServers()).isEqualTo(null);
    assertThat(mysqlPlugin.getPerfEventsStatementsDigestTextLimit()).isEqualTo(null);
    assertThat(mysqlPlugin.getPerfEventsStatementsLimit()).isEqualTo(null);
    assertThat(mysqlPlugin.getPerfEventsStatementsTimeLimit()).isEqualTo(null);
    assertThat(mysqlPlugin.getTableSchemaDatabases()).isEqualTo(null);
    assertThat(mysqlPlugin.isGatherProcessList()).isFalse();
    assertThat(mysqlPlugin.isGatherUserStatistics()).isFalse();
    assertThat(mysqlPlugin.isGatherInfoSchemaAutoInc()).isFalse();
    assertThat(mysqlPlugin.isGatherInnodbMetrics()).isFalse();
    assertThat(mysqlPlugin.isGatherSlaveStatus()).isFalse();
    assertThat(mysqlPlugin.isGatherBinaryLogs()).isFalse();
    assertThat(mysqlPlugin.isGatherTableIoWaits()).isFalse();
    assertThat(mysqlPlugin.isGatherTableLockWaits()).isFalse();
    assertThat(mysqlPlugin.isGatherIndexIoWaits()).isFalse();
    assertThat(mysqlPlugin.isGatherEventWaits()).isFalse();
    assertThat(mysqlPlugin.isGatherTableSchema()).isFalse();
    assertThat(mysqlPlugin.isGatherFileEventsStats()).isFalse();
    assertThat(mysqlPlugin.isGatherPerfEventsStatements()).isFalse();
    assertThat(mysqlPlugin.getIntervalSlow()).isEqualTo(null);
    assertThat(mysqlPlugin.getMetricVersion()).isEqualTo(2);
    assertThat(mysqlPlugin.getTlsCa()).isEqualTo(null);
    assertThat(mysqlPlugin.getTlsCert()).isEqualTo(null);
    assertThat(mysqlPlugin.getTlsKey()).isEqualTo(null);

  }

  @Test
  public void convertFromInput_mysqlremote() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput");

    List<String> l = List.of("1","2");
    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    final MysqlRemote plugin = new MysqlRemote();
    plugin.setServers(l);
    plugin.setPerfEventsStatementsDigestTextLimit(1);
    plugin.setPerfEventsStatementsLimit(2);
    plugin.setPerfEventsStatementsTimeLimit(3);
    plugin.setTableSchemaDatabases(l);
    plugin.setGatherProcessList(false);
    plugin.setGatherUserStatistics(true);
    plugin.setGatherInfoSchemaAutoInc(false);
    plugin.setGatherInnodbMetrics(true);
    plugin.setGatherSlaveStatus(false);
    plugin.setGatherBinaryLogs(true);
    plugin.setGatherTableIoWaits(false);
    plugin.setGatherTableLockWaits(true);
    plugin.setGatherIndexIoWaits(false);
    plugin.setGatherEventWaits(true);
    plugin.setGatherTableSchema(false);
    plugin.setGatherFileEventsStats(true);
    plugin.setGatherPerfEventsStatements(false);
    plugin.setIntervalSlow("3s");
    plugin.setMetricVersion(2);
    plugin.setTlsCa("tlsCa");
    plugin.setTlsCert("tlsCert");
    plugin.setTlsKey("tlsKey");
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
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_mysql.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

}
