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

package com.rackspace.salus.monitor_management.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.monitor_management.web.model.telegraf.*;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.validation.ConstraintViolation;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@RunWith(SpringRunner.class)
@JsonTest
@Import({MonitorConversionService.class})
public class MonitorConversionServiceTest {

  @Configuration
  public static class TestConfig {

  }

  @Autowired
  MonitorConversionService conversionService;

  @Test
  public void convertToOutput_local() throws IOException {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertToOutput_local");

    final String content = readContent("/MonitorConversionServiceTest_cpu.json");

    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setMonitorName("name-a")
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelector(labels)
        .setContent(content);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitorId.toString());
    assertThat(result.getName()).isEqualTo("name-a");
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getDetails()).isInstanceOf(LocalMonitorDetails.class);

    final LocalPlugin plugin = ((LocalMonitorDetails) result.getDetails()).getPlugin();
    assertThat(plugin).isInstanceOf(Cpu.class);

    final Cpu cpuPlugin = (Cpu) plugin;
    assertThat(cpuPlugin.isCollectCpuTime()).isFalse();
    assertThat(cpuPlugin.isPercpu()).isFalse();
    assertThat(cpuPlugin.isReportActive()).isFalse();
    assertThat(cpuPlugin.isTotalcpu()).isFalse();
  }

  @Test
  public void convertFromInput_local() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput_local");

    final LocalMonitorDetails details = new LocalMonitorDetails();
    final Cpu plugin = new Cpu();
    plugin.setPercpu(false);
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
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.LOCAL);
    final String content = readContent("/MonitorConversionServiceTest_cpu.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertToOutput_disk() throws IOException {
    // NOTE: this unit test is purposely abbreviated compared convertToOutput

    final String content = readContent("/MonitorConversionServiceTest_disk.json");

    Monitor monitor = new Monitor()
        .setId(UUID.randomUUID())
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelector(Collections.singletonMap("os","linux"))
        .setContent(content);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getDetails()).isInstanceOf(LocalMonitorDetails.class);

    final LocalPlugin plugin = ((LocalMonitorDetails) result.getDetails()).getPlugin();
    assertThat(plugin).isInstanceOf(Disk.class);

    final Disk specificPlugin = (Disk) plugin;
    assertThat(specificPlugin.getMountPoints()).contains("/var/lib");
    assertThat(specificPlugin.getIgnoreFs()).contains("/dev");
  }

  @Test
  public void convertFromInput_disk() throws JSONException, IOException {
    // NOTE: this unit test is purposely abbreviated compared convertFromInput

    final String content = readContent("/MonitorConversionServiceTest_disk.json");

    final Disk plugin = new Disk();
    plugin.setMountPoints(Collections.singletonList("/var/lib"));
    plugin.setIgnoreFs(Collections.singletonList("/dev"));

    final LocalMonitorDetails details = new LocalMonitorDetails();
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setLabelSelector(Collections.singletonMap("os","linux"))
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(input);

    assertThat(result).isNotNull();
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertToOutput_diskio() throws IOException {
    // NOTE: this unit test is purposely abbreviated compared convertToOutput

    final String content = readContent("/MonitorConversionServiceTest_diskio.json");

    Monitor monitor = new Monitor()
        .setId(UUID.randomUUID())
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelector(Collections.singletonMap("os","linux"))
        .setContent(content);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getDetails()).isInstanceOf(LocalMonitorDetails.class);

    final LocalPlugin plugin = ((LocalMonitorDetails) result.getDetails()).getPlugin();
    assertThat(plugin).isInstanceOf(DiskIo.class);

    final DiskIo specificPlugin = (DiskIo) plugin;
    assertThat(specificPlugin.getDevices()).contains("sda");
    assertThat(specificPlugin.getSkipSerialNumber()).isTrue();
    assertThat(specificPlugin.getDeviceTags()).contains("ID_FS_TYPE");
    assertThat(specificPlugin.getNameTemplates()).contains("$ID_FS_LABEL");

  }

  @Test
  public void convertFromInput_diskio() throws JSONException, IOException {
    // NOTE: this unit test is purposely abbreviated compared convertFromInput

    final String content = readContent("/MonitorConversionServiceTest_diskio.json");

    final DiskIo plugin = new DiskIo();
    plugin.setDevices(Collections.singletonList("sda"));
    plugin.setSkipSerialNumber(true);
    plugin.setDeviceTags(Collections.singletonList("ID_FS_TYPE"));
    plugin.setNameTemplates(Collections.singletonList("$ID_FS_LABEL"));


    final LocalMonitorDetails details = new LocalMonitorDetails();
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setLabelSelector(Collections.singletonMap("os","linux"))
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(input);

    assertThat(result).isNotNull();
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertToOutput_mem() throws IOException {
    // NOTE: this unit test is purposely abbreviated compared convertToOutput

    final String content = readContent("/MonitorConversionServiceTest_mem.json");

    Monitor monitor = new Monitor()
        .setId(UUID.randomUUID())
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelector(Collections.singletonMap("os","linux"))
        .setContent(content);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getDetails()).isInstanceOf(LocalMonitorDetails.class);

    final LocalPlugin plugin = ((LocalMonitorDetails) result.getDetails()).getPlugin();
    assertThat(plugin).isInstanceOf(Mem.class);
    // no config to validate
  }

 @Test
  public void convertFromInput_mem() throws JSONException, IOException {
    // NOTE: this unit test is purposely abbreviated compared convertFromInput

    final String content = readContent("/MonitorConversionServiceTest_mem.json");

    final Mem plugin = new Mem();
    // no config to set

    final LocalMonitorDetails details = new LocalMonitorDetails();
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setLabelSelector(Collections.singletonMap("os","linux"))
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(input);

    assertThat(result).isNotNull();
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertToOutput_ping() throws IOException {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertToOutput_remote");

    final String content = readContent("/MonitorConversionServiceTest_ping.json");

    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setMonitorName("name-a")
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setZones(Collections.singletonList("z-1"))
        .setLabelSelector(labels)
        .setContent(content);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitorId.toString());
    assertThat(result.getName()).isEqualTo("name-a");
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getDetails()).isInstanceOf(RemoteMonitorDetails.class);

    final RemoteMonitorDetails remoteMonitorDetails = (RemoteMonitorDetails) result.getDetails();
    assertThat(remoteMonitorDetails.getMonitoringZones()).contains("z-1");
    final RemotePlugin plugin = remoteMonitorDetails.getPlugin();
    assertThat(plugin).isInstanceOf(Ping.class);

    final Ping pingPlugin = (Ping) plugin;
    assertThat(pingPlugin.getUrls()).contains("localhost");
  }

  @Test
  public void convertFromInput_ping() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput_ping");

    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    details.setMonitoringZones(Collections.singletonList("z-1"));
    final Ping plugin = new Ping();
    plugin.setUrls(Collections.singletonList("localhost"));
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
    final String content = readContent("/MonitorConversionServiceTest_ping.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void testValidationOfLabelSelectors() {
    final LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();

    Map<String, String> labels = new HashMap<>();
    labels.put("agent.discovered.os", "linux");

    final DetailedMonitorInput input = new DetailedMonitorInput()
        .setLabelSelector(labels)
        .setDetails(
            new LocalMonitorDetails()
            .setPlugin(new Mem())
        );
    final Set<ConstraintViolation<DetailedMonitorInput>> results = validatorFactoryBean.validate(input);

    Assert.assertThat(results.size(), equalTo(1));
    final ConstraintViolation<DetailedMonitorInput> violation = results.iterator().next();
    Assert.assertThat(violation.getPropertyPath().toString(), equalTo("labelSelector"));
    Assert.assertThat(violation.getMessage(), equalTo("All label names must consist of alpha-numeric or underscore characters"));
  }

  @Test
  public void convertFromInput_procstat() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput_ping");

    final LocalMonitorDetails details = new LocalMonitorDetails();
    final Procstat plugin = new Procstat();
    plugin.setPidFile("/path/to/file");
    plugin.setProcessName("thisIsAProcess");
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
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.LOCAL);
    final String content = readContent("/MonitorConversionServiceTest_procstat.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertToOutput_procstat() throws IOException {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertToOutput_remote");

    final String content = readContent("/MonitorConversionServiceTest_procstat.json");

    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
            .setId(monitorId)
            .setMonitorName("name-a")
            .setAgentType(AgentType.TELEGRAF)
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setLabelSelector(labels)
            .setContent(content);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitorId.toString());
    assertThat(result.getName()).isEqualTo("name-a");
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getDetails()).isInstanceOf(LocalMonitorDetails.class);

    final LocalMonitorDetails localMonitorDetails = (LocalMonitorDetails) result.getDetails();
    final LocalPlugin plugin = localMonitorDetails.getPlugin();
    assertThat(plugin).isInstanceOf(Procstat.class);

    final Procstat procstatPlugin = (Procstat) plugin;
    assertThat(procstatPlugin.getPidFile()).contains("/path/to/file");
    assertThat(procstatPlugin.getProcessName()).contains("thisIsAProcess");
  }

  private static String readContent(String resource) throws IOException {
    try (InputStream in = new ClassPathResource(resource).getInputStream()) {
      return FileCopyUtils.copyToString(new InputStreamReader(in));
    }
  }
}