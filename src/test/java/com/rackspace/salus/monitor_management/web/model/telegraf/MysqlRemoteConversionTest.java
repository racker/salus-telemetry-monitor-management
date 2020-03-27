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

package com.rackspace.salus.monitor_management.web.model.telegraf;

import static com.rackspace.salus.monitor_management.web.model.ConversionHelpers.assertCommonRemote;
import static com.rackspace.salus.monitor_management.web.model.ConversionHelpers.createMonitor;
import static com.rackspace.salus.monitor_management.web.model.ConversionHelpers.nullableSummaryValue;
import static com.rackspace.salus.test.JsonTestUtils.readContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.io.IOException;
import java.time.Duration;
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
public class MysqlRemoteConversionTest {
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
  public void convertToOutput_mysqlremote() throws IOException {
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_mysql.json");

    Monitor monitor = createMonitor(content, "convertToOutput", AgentType.TELEGRAF,
        ConfigSelectorScope.REMOTE
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final MysqlRemote mysqlPlugin = assertCommonRemote(result, monitor, MysqlRemote.class, "convertToOutput",
        Map.of("servers", "[1, 2]"));

    List<String> l = List.of("1","2");
    assertThat(mysqlPlugin.getServers()).isEqualTo(l);
    assertThat(mysqlPlugin.getPerfEventsStatementsDigestTextLimit()).isEqualTo(1);
    assertThat(mysqlPlugin.getPerfEventsStatementsLimit()).isEqualTo(2);
    assertThat(mysqlPlugin.getPerfEventsStatementsTimeLimit()).isEqualTo(3);
    assertThat(mysqlPlugin.getTableSchemaDatabases()).isEqualTo(l);
    assertThat(mysqlPlugin.getGatherProcessList()).isFalse();
    assertThat(mysqlPlugin.getGatherUserStatistics()).isTrue();
    assertThat(mysqlPlugin.getGatherInfoSchemaAutoInc()).isFalse();
    assertThat(mysqlPlugin.getGatherInnodbMetrics()).isTrue();
    assertThat(mysqlPlugin.getGatherSlaveStatus()).isFalse();
    assertThat(mysqlPlugin.getGatherBinaryLogs()).isTrue();
    assertThat(mysqlPlugin.getGatherTableIoWaits()).isFalse();
    assertThat(mysqlPlugin.getGatherTableLockWaits()).isTrue();
    assertThat(mysqlPlugin.getGatherIndexIoWaits()).isFalse();
    assertThat(mysqlPlugin.getGatherEventWaits()).isTrue();
    assertThat(mysqlPlugin.getGatherTableSchema()).isFalse();
    assertThat(mysqlPlugin.getGatherFileEventsStats()).isTrue();
    assertThat(mysqlPlugin.getGatherPerfEventsStatements()).isFalse();
    assertThat(mysqlPlugin.getIntervalSlow()).isEqualTo(Duration.ofSeconds(3));
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

    final MysqlRemote mysqlPlugin = assertCommonRemote(result, monitor, MysqlRemote.class, "convertToOutput_defaults",
        nullableSummaryValue("servers"));
    assertThat(mysqlPlugin.getServers()).isEqualTo(null);
    assertThat(mysqlPlugin.getPerfEventsStatementsDigestTextLimit()).isEqualTo(null);
    assertThat(mysqlPlugin.getPerfEventsStatementsLimit()).isEqualTo(null);
    assertThat(mysqlPlugin.getPerfEventsStatementsTimeLimit()).isEqualTo(null);
    assertThat(mysqlPlugin.getTableSchemaDatabases()).isEqualTo(null);
    assertThat(mysqlPlugin.getGatherProcessList()).isNull();
    assertThat(mysqlPlugin.getGatherUserStatistics()).isNull();
    assertThat(mysqlPlugin.getGatherInfoSchemaAutoInc()).isNull();
    assertThat(mysqlPlugin.getGatherInnodbMetrics()).isNull();
    assertThat(mysqlPlugin.getGatherSlaveStatus()).isNull();
    assertThat(mysqlPlugin.getGatherBinaryLogs()).isNull();
    assertThat(mysqlPlugin.getGatherTableIoWaits()).isNull();
    assertThat(mysqlPlugin.getGatherTableLockWaits()).isNull();
    assertThat(mysqlPlugin.getGatherIndexIoWaits()).isNull();
    assertThat(mysqlPlugin.getGatherEventWaits()).isNull();
    assertThat(mysqlPlugin.getGatherTableSchema()).isNull();
    assertThat(mysqlPlugin.getGatherFileEventsStats()).isNull();
    assertThat(mysqlPlugin.getGatherPerfEventsStatements()).isNull();
    assertThat(mysqlPlugin.getIntervalSlow()).isEqualTo(null);
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
    plugin.setIntervalSlow(Duration.ofSeconds(3));
    plugin.setTlsCa("tlsCa");
    plugin.setTlsCert("tlsCert");
    plugin.setTlsKey("tlsKey");
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
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_mysql.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

}
