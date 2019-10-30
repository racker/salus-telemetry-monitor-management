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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static com.rackspace.salus.common.util.SpringResourceUtils.readContent;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.JsonConfig;
import com.rackspace.salus.monitor_management.config.MonitorConversionProperties;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.monitor_management.web.model.telegraf.Cpu;
import com.rackspace.salus.monitor_management.web.model.telegraf.DiskIo;
import com.rackspace.salus.monitor_management.web.model.telegraf.HttpResponse;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mem;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.monitor_management.web.model.telegraf.Procstat;
import com.rackspace.salus.monitor_management.web.model.telegraf.System;
import com.rackspace.salus.monitor_management.web.model.telegraf.X509Cert;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MetadataValueType;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.TargetClassName;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonPatch;
import javax.json.JsonValue;
import javax.validation.ConstraintViolation;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {MonitorConversionService.class, MetadataUtils.class,
    PatchHelper.class, JsonConfig.class})
@AutoConfigureJson
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
public class MonitorConversionServiceTest {

  private static final Duration MIN_INTERVAL = Duration.ofSeconds(10);
  private static final Duration DEFAULT_LOCAL_INTERVAL = Duration.ofSeconds(30);
  private static final Duration DEFAULT_REMOTE_INTERVAL = Duration.ofMinutes(5);

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Autowired
  MonitorConversionService conversionService;

  @Autowired
  MonitorConversionProperties monitorConversionProperties;

  @Autowired
  MetadataUtils metadataUtils;

  @Autowired
  PatchHelper patchHelper;

  @Autowired
  ObjectMapper objectMapper;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  MonitorRepository monitorRepository;

  @Before
  public void setUp() throws Exception {
    monitorConversionProperties
        .setMinimumAllowedInterval(MIN_INTERVAL)
        .setDefaultLocalInterval(DEFAULT_LOCAL_INTERVAL)
        .setDefaultRemoteInterval(DEFAULT_REMOTE_INTERVAL);
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
        .setLabelSelectorMethod(LabelSelectorMethod.OR)
        .setContent(content)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

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
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

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
        .setContent(content)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

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
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

    assertThat(result).isNotNull();
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertToOutput_system() throws IOException {
    final String content = readContent("/MonitorConversionServiceTest_system.json");

    Monitor monitor = new Monitor()
        .setId(UUID.randomUUID())
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelector(Collections.singletonMap("os", "linux"))
        .setContent(content)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getDetails()).isInstanceOf(LocalMonitorDetails.class);

    final LocalPlugin plugin = ((LocalMonitorDetails) result.getDetails()).getPlugin();
    assertThat(plugin).isInstanceOf(System.class);
    // no config to validate
  }

  @Test
  public void convertFromInput_system() throws JSONException, IOException {
    final String content = readContent("/MonitorConversionServiceTest_system.json");

    final System plugin = new System();
    // no config to set

    final LocalMonitorDetails details = new LocalMonitorDetails();
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setLabelSelector(Collections.singletonMap("os", "linux"))
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

    assertThat(result).isNotNull();
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertToOutput_ping() throws IOException {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertToOutput_remote");

    final String content = readContent("/MonitorConversionServiceTest_ping_with_policy.json");

    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setMonitorName("name-a")
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setZones(Collections.singletonList("z-1"))
        .setLabelSelector(labels)
        .setContent(content)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitorId.toString());
    assertThat(result.getName()).isEqualTo("name-a");
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getLabelSelectorMethod()).isEqualTo(LabelSelectorMethod.AND);
    assertThat(result.getDetails()).isInstanceOf(RemoteMonitorDetails.class);

    final RemoteMonitorDetails remoteMonitorDetails = (RemoteMonitorDetails) result.getDetails();
    assertThat(remoteMonitorDetails.getMonitoringZones()).contains("z-1");
    final RemotePlugin plugin = remoteMonitorDetails.getPlugin();
    assertThat(plugin).isInstanceOf(Ping.class);

    final Ping pingPlugin = (Ping) plugin;
    assertThat(pingPlugin.getUrls()).contains("localhost");
    assertThat(pingPlugin.getCount()).isEqualTo(63);
    assertThat(pingPlugin.getPingInterval()).isEqualTo(2);
    assertThat(pingPlugin.getDeadline()).isNull();
    assertThat(pingPlugin.getInterfaceOrAddress()).isNull();
    assertThat(pingPlugin.getTimeout()).isNull();
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
        .setLabelSelectorMethod(LabelSelectorMethod.OR)
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

    assertThat(result).isNotNull();
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getLabelSelectorMethod()).isEqualTo(LabelSelectorMethod.OR);
    assertThat(result.getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(result.getMonitorName()).isEqualTo("name-a");
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.REMOTE);
    final String content = readContent("/MonitorConversionServiceTest_ping.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertFromInput_ping_withPluginPolicies() throws JSONException, IOException {
    Map<String, MonitorMetadataPolicyDTO> expectedPolicy = Map.of(
        "count", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("count")
            .setValueType(MetadataValueType.INT)
            .setValue("63"),
        "pingInterval", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("pingInterval")
            .setValueType(MetadataValueType.INT)
            .setValue("2"));

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any()))
        .thenReturn(expectedPolicy);

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
        .setLabelSelectorMethod(LabelSelectorMethod.OR)
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

    assertThat(result).isNotNull();
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getLabelSelectorMethod()).isEqualTo(LabelSelectorMethod.OR);
    assertThat(result.getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(result.getMonitorName()).isEqualTo("name-a");
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.REMOTE);

    final String content = readContent("/MonitorConversionServiceTest_ping_with_policy.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertFromPatchInput_ping_resetPolicies()
      throws JSONException, IOException {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID monitorId = UUID.randomUUID();

    Map<String, MonitorMetadataPolicyDTO> expectedPolicy = Map.of(
        "count", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("count")
            .setValueType(MetadataValueType.INT)
            .setValue("63"),
        "pingInterval", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("pingInterval")
            .setValueType(MetadataValueType.INT)
            .setValue("2"),
        "interval", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("interval")
            .setValueType(MetadataValueType.INT)
            .setValue("44"),
        "zones", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("zones")
            .setValueType(MetadataValueType.STRING_LIST)
            .setValue("defaultZone1,defaultZone2"));

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any()))
        .thenReturn(expectedPolicy);

    // Create the plugin that will be converted to the monitor's contents
    // timeout is set to null to show we can set values that were not set before
    final Ping plugin = new Ping()
        .setUrls(Collections.singletonList("localhost"))
        .setCount(1)
        .setPingInterval(2)
        .setTimeout(null);

    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convert");

    // Create the original Monitor that will be patched
    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setMonitorName("name-a")
        .setZones(Collections.singletonList("z-1"))
        .setInterval(Duration.ofSeconds(100))
        .setContent(objectMapper.writeValueAsString(plugin))
        .setLabelSelector(labels)
        .setLabelSelectorMethod(LabelSelectorMethod.OR)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorMetadataFields(Collections.emptyList())
        .setPluginMetadataFields(Collections.emptyList())
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

    // Create the patch payload which only contains the fields to change
    // It builds out the following:
    /*
    [
      {
        "op": "replace",
        "path": "/interval",
        "value": null
      },
      {
        "op": "replace",
        "path": "/details/monitoringZones",
        "value": null
      },
      {
        "op": "replace",
        "path": "/details/plugin/count",
        "value": null
      },
      {
        "op": "replace",
        "path": "/details/plugin/pingInterval",
        "value": null
      },
      {
        "op": "replace",
        "path": "/details/plugin/timeout",
        "value": 33
      }
    ]*/
    JsonPatch patch = Json.createPatch(Json.createArrayBuilder()
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/interval")
            .add("value", JsonValue.NULL))
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/details/monitoringZones")
            .add("value", JsonValue.NULL))
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/details/plugin/count")
            .add("value", JsonValue.NULL))
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/details/plugin/pingInterval")
            .add("value", JsonValue.NULL))
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/details/plugin/timeout")
            .add("value", 33))
        .build());

    final MonitorCU result = conversionService.convertFromPatchInput(
        tenantId, monitorId, monitor, patch);

    assertThat(result).isNotNull();
    // unchanged fields
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getLabelSelectorMethod()).isEqualTo(LabelSelectorMethod.OR);
    assertThat(result.getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(result.getMonitorName()).isEqualTo("name-a");
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.REMOTE);

    // fields changed by patch
    final String content = readContent("/MonitorConversionServiceTest_patch_with_policy.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
    // these are still null since they are top level fields handled by MonitorManagement
    assertThat(result.getInterval()).isEqualTo(null);
    assertThat(result.getZones()).isEqualTo(null);
  }

  @Test
  public void convertFromPatchInput_ping_testTopLevelValidation()
      throws IOException {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID monitorId = UUID.randomUUID();

    // Create the plugin that will be converted to the monitor's contents
    final Ping plugin = new Ping()
        .setUrls(Collections.singletonList("localhost"))
        .setCount(1)
        .setPingInterval(2)
        .setTimeout(3);

    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convert");

    // Create the original Monitor that will be patched
    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setMonitorName("name-a")
        .setZones(Collections.singletonList("z-1"))
        .setInterval(Duration.ofSeconds(100))
        .setContent(objectMapper.writeValueAsString(plugin))
        .setLabelSelector(labels)
        .setLabelSelectorMethod(LabelSelectorMethod.OR)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorMetadataFields(Collections.emptyList())
        .setPluginMetadataFields(Collections.emptyList())
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

    // Create the patch payload which only contains the fields to change
    // Details has non-null validation and should trigger an exception
    JsonPatch patch = Json.createPatch(Json.createArrayBuilder()
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/interval")
            .add("value", JsonValue.NULL))
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/details")
            .add("value", JsonValue.NULL))
        .build());

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("details: must not be null");
    conversionService.convertFromPatchInput(tenantId, monitorId, monitor, patch);
  }

  @Test
  public void convertFromPatchInput_ping_testPluginFieldValidation()
      throws IOException {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID monitorId = UUID.randomUUID();

    // Create the plugin that will be converted to the monitor's contents
    final Ping plugin = new Ping()
        .setUrls(Collections.singletonList("localhost"))
        .setCount(1)
        .setPingInterval(2)
        .setTimeout(3);

    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convert");

    // Create the original Monitor that will be patched
    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setMonitorName("name-a")
        .setZones(Collections.singletonList("z-1"))
        .setInterval(Duration.ofSeconds(100))
        .setContent(objectMapper.writeValueAsString(plugin))
        .setLabelSelector(labels)
        .setLabelSelectorMethod(LabelSelectorMethod.OR)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorMetadataFields(Collections.emptyList())
        .setPluginMetadataFields(Collections.emptyList())
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

    // Create the patch payload which only contains the fields to change
    // Urls has non-empty validation and should trigger an exception
    JsonPatch patch = Json.createPatch(Json.createArrayBuilder()
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/interval")
            .add("value", JsonValue.NULL))
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/details/monitoringZones")
            .add("value", JsonValue.NULL))
        .add(Json.createObjectBuilder()
            .add("op", "replace")
            .add("path", "/details/plugin/urls")
            .add("value", JsonValue.NULL))
        .build());

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("details.plugin.urls: must not be empty");
    conversionService.convertFromPatchInput(tenantId, monitorId, monitor, patch);
  }

  @Test
  public void convertToOutput_x509() throws IOException {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertToOutput_x509");

    final String content = readContent("/MonitorConversionServiceTest_x509.json");

    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setMonitorName("name-a")
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setZones(Collections.singletonList("z-1"))
        .setLabelSelector(labels)
        .setContent(content)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitorId.toString());
    assertThat(result.getName()).isEqualTo("name-a");
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getDetails()).isInstanceOf(RemoteMonitorDetails.class);

    final RemoteMonitorDetails remoteMonitorDetails = (RemoteMonitorDetails) result.getDetails();
    assertThat(remoteMonitorDetails.getMonitoringZones()).contains("z-1");
    final RemotePlugin plugin = remoteMonitorDetails.getPlugin();
    assertThat(plugin).isInstanceOf(X509Cert.class);

    final X509Cert x509Plugin = (X509Cert) plugin;
    assertThat(x509Plugin.getSources()).contains("/etc/ssl/certs/ssl-cert-snakeoil.pem");
    assertThat(x509Plugin.getTimeout()).isEqualTo("5s");
    assertThat(x509Plugin.getTlsCa()).isEqualTo("/etc/telegraf/ca.pem");
    assertThat(x509Plugin.getTlsCert()).isEqualTo("/etc/telegraf/cert.pem");
    assertThat(x509Plugin.getTlsKey()).isEqualTo("/etc/telegraf/key.pem");
    assertThat(x509Plugin.isInsecureSkipVerify()).isEqualTo(false);

    final LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
    Set<ConstraintViolation<X509Cert>> violations = validatorFactoryBean.validate(x509Plugin);
    assertEquals(violations.size(), 0);
    x509Plugin.setTimeout("xx");
    violations = validatorFactoryBean.validate(x509Plugin);
    assertEquals(violations.size(), 1);
    x509Plugin.setTimeout("300ms");
    violations = validatorFactoryBean.validate(x509Plugin);
    assertEquals(violations.size(), 0);
  }

  @Test
  public void convertFromInput_x509() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput_x509");
    final List<String> sources = new LinkedList<>();
    sources.add("/etc/ssl/certs/ssl-cert-snakeoil.pem");

    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    details.setMonitoringZones(Collections.singletonList("z-1"));
    final X509Cert plugin = new X509Cert();
    plugin.setSources(sources);
    plugin.setTimeout("5s");
    plugin.setTlsCa("/etc/telegraf/ca.pem");
    plugin.setTlsCert("/etc/telegraf/cert.pem");
    plugin.setTlsKey("/etc/telegraf/key.pem");
    plugin.setInsecureSkipVerify(false);
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setName("name-a")
        .setLabelSelector(labels)
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

    assertThat(result).isNotNull();
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(result.getMonitorName()).isEqualTo("name-a");
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.REMOTE);
    final String content = readContent("/MonitorConversionServiceTest_x509.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertToOutput_http_response() throws IOException {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertToOutput_http");

    final String content = readContent("/MonitorConversionServiceTest_http.json");
    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setMonitorName("name-a")
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setZones(Collections.singletonList("z-1"))
        .setLabelSelector(labels)
        .setContent(content)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitorId.toString());
    assertThat(result.getName()).isEqualTo("name-a");
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getDetails()).isInstanceOf(RemoteMonitorDetails.class);

    final RemoteMonitorDetails remoteMonitorDetails = (RemoteMonitorDetails) result.getDetails();
    assertThat(remoteMonitorDetails.getMonitoringZones()).contains("z-1");
    final RemotePlugin plugin = remoteMonitorDetails.getPlugin();
    assertThat(plugin).isInstanceOf(HttpResponse.class);

    final HttpResponse httpPlugin = (HttpResponse) plugin;
    assertThat(httpPlugin.getAddress()).isEqualTo("http://localhost");
    assertThat(httpPlugin.getHttpProxy()).isEqualTo("http://localhost:8888");
    assertThat(httpPlugin.getResponseTimeout()).isEqualTo("5s");
    assertThat(httpPlugin.getMethod()).isEqualTo("GET");
    assertThat(httpPlugin.isFollowRedirects()).isEqualTo(false);
    assertThat(httpPlugin.getBody()).isEqualTo("{'fake':'data'}");
    assertThat(httpPlugin.getResponseStringMatch()).isEqualTo("\"service_status\": \"up\"");
    assertThat(httpPlugin.getTlsCa()).isEqualTo("/etc/telegraf/ca.pem");
    assertThat(httpPlugin.getTlsCert()).isEqualTo("/etc/telegraf/cert.pem");
    assertThat(httpPlugin.getTlsKey()).isEqualTo("/etc/telegraf/key.pem");
    assertThat(httpPlugin.isInsecureSkipVerify()).isEqualTo(false);
    assertThat(httpPlugin.getHeaders().get("host")).isEqualTo("github.com");

    final LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
    Set<ConstraintViolation<HttpResponse>> violations = validatorFactoryBean.validate(httpPlugin);
    assertEquals(violations.size(), 0);
    httpPlugin.setMethod("badMethod");
    violations = validatorFactoryBean.validate(httpPlugin);
    assertEquals(violations.size(), 1);
  }

  @Test
  public void convertFromInput_http_response() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput_http");

    final Map<String, String> headers = new HashMap<>();
    headers.put("host", "github.com");

    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    details.setMonitoringZones(Collections.singletonList("z-1"));
    final HttpResponse plugin = new HttpResponse();
    plugin.setAddress("http://localhost");
    plugin.setHttpProxy("http://localhost:8888");
    plugin.setResponseTimeout("5s");
    plugin.setMethod("GET");
    plugin.setFollowRedirects(false);
    plugin.setBody("{'fake':'data'}");
    plugin.setResponseStringMatch("\"service_status\": \"up\"");
    plugin.setTlsCa("/etc/telegraf/ca.pem");
    plugin.setTlsCert("/etc/telegraf/cert.pem");
    plugin.setTlsKey("/etc/telegraf/key.pem");
    plugin.setInsecureSkipVerify(false);
    plugin.setHeaders(headers);
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setName("name-a")
        .setLabelSelector(labels)
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

    assertThat(result).isNotNull();
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(result.getMonitorName()).isEqualTo("name-a");
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.REMOTE);
    final String content = readContent("/MonitorConversionServiceTest_http.json");
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
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

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
            .setContent(content)
            .setCreatedTimestamp(Instant.EPOCH)
            .setUpdatedTimestamp(Instant.EPOCH);

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

  @Test
  public void testConvertFrom_ResourceId() {
    DetailedMonitorInput input = new DetailedMonitorInput()
        .setResourceId("r-1")
        .setDetails(new LocalMonitorDetails().setPlugin(new Mem()));
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);
    assertThat(result.getResourceId()).isEqualTo(input.getResourceId());
  }

  @Test
  public void testConvertTo_ResourceId() {
    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setResourceId("r-1")
        .setId(monitorId)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);
    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);
    assertThat(result.getResourceId()).isEqualTo(monitor.getResourceId());
  }

  @Test
  public void testConvertTo_LabelSelectorMethod() {
    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setId(monitorId)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);
    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);
    assertThat(result.getLabelSelectorMethod()).isEqualTo(monitor.getLabelSelectorMethod());
  }

  @Test
  public void testConvertFrom_LabelSelectorMethod() {
    DetailedMonitorInput input = new DetailedMonitorInput()
        .setLabelSelectorMethod(LabelSelectorMethod.OR)
        .setDetails(new LocalMonitorDetails().setPlugin(new Cpu()));
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);
    assertThat(result.getLabelSelectorMethod()).isEqualTo(input.getLabelSelectorMethod());
  }

  @Test
  public void testConvertTo_Interval() {
    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setId(monitorId)
        .setInterval(Duration.ofSeconds(60))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);
    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);
    assertThat(result.getInterval()).isEqualTo(monitor.getInterval());
  }

  @Test
  public void testConvertFrom_Interval_Explicit() {
    final Duration interval = Duration.ofSeconds(60);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setInterval(interval);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);
    assertThat(result.getInterval()).isEqualTo(interval);
  }

  @Test
  public void testConvertFrom_Interval_Explicit_ExactlyMin() {
    final Duration interval = MIN_INTERVAL;

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setInterval(interval);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);
    assertThat(result.getInterval()).isEqualTo(interval);
  }

  @Test
  public void testConvertFrom_Interval_Explicit_LessThanMin() {
    final Duration interval = MIN_INTERVAL.minus(Duration.ofSeconds(1));

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setInterval(interval);

    assertThatThrownBy(() -> {
      conversionService.convertFromInput(
          RandomStringUtils.randomAlphabetic(10), null, input);
    })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(String.format("Interval cannot be less than %s", MIN_INTERVAL));
  }

  @Test
  public void testConvertFrom_Interval_DefaultLocal_noMetadata() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setInterval(null);
    Map<String, MonitorMetadataPolicyDTO> expectedPolicy = Map.of("interval",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setValueType(MetadataValueType.DURATION)
            .setValue("300"));

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any()))
        .thenReturn(expectedPolicy);

    final MonitorCU result = conversionService.convertFromInput(
        tenantId, null, input);
    // interval is set on the Monitor not the plugin, so it will remain null at this stage
    // even though interval metadata was also seen for the plugin object.
    assertThat(result.getInterval()).isNull();

    // mem has no metadata fields so no interactions with api occur
    verifyNoMoreInteractions(policyApi);
  }

  @Test
  public void testConvertFrom_Interval_DefaultRemote() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(new RemoteMonitorDetails().setPlugin(new Ping()))
        .setInterval(null);

    Map<String, MonitorMetadataPolicyDTO> expectedPolicy = Map.of("interval",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setValueType(MetadataValueType.DURATION)
            .setValue("300"));

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any()))
        .thenReturn(expectedPolicy);

    final MonitorCU result = conversionService.convertFromInput(
        tenantId, null, input);
    // interval is set on the Monitor not the plugin, so it will remain null at this stage
    // even though interval metadata was also seen for the plugin object.
    assertThat(result.getInterval()).isNull();

    // ping has metadata fields so the api would have been called
    verify(policyApi).getEffectiveMonitorMetadataMap(tenantId, TargetClassName.RemotePlugin, MonitorType.ping);
  }
}