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

package com.rackspace.salus.monitor_management.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.rackspace.salus.monitor_management.web.model.telegraf.Disk;
import com.rackspace.salus.monitor_management.web.model.telegraf.HttpResponse;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.telemetry.entities.MetadataPolicy;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.MetadataValueType;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.TargetClassName;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {MetadataUtils.class})
public class MetadataUtilsTest {

  @MockBean
  PolicyApi policyApi;

  @Autowired
  MetadataUtils metadataUtils;

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void getMetadataFieldsForCreate_Ping() {
    assertThat(MetadataUtils.getMetadataFieldsForCreate(new Ping()))
        .containsAll(List.of(
            "count",
            "pingInterval",
            "timeout",
            "deadline"
        ));
  }

  @Test
  public void getMetadataFieldsForUpdate_Ping() {
    Ping ping = new Ping()
        .setCount(3)
        .setTimeout(Duration.ofSeconds(10))
        .setPingInterval(Duration.ofSeconds(20));

    Map<String, MonitorMetadataPolicyDTO> policies = Map.of(
        "count", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("count")
            .setValueType(MetadataValueType.INT)
            .setValue("5"),
        "timeout", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("timeout")
            .setValueType(MetadataValueType.DURATION)
            .setValue("PT10S")
    );

    // count has a different value than the policy.  even though it was previously using metadata, it is now excluded
    // timeout has the same value as the metadata and was previously set using it, so it remains as metadata
    // pingInterval was not previously using metadata so it is still not included in the results
    // deadline is set to null so it will be set to metadata
    // interfaceOrAddress is set to null so it will be set to metadata
    assertThat(MetadataUtils.getMetadataFieldsForUpdate(ping, List.of("count", "timeout"), policies))
        .containsAll(List.of("timeout", "deadline"));
  }

  @Test
  public void getMetadataFieldsForUpdate_Http() {
    HttpResponse ping = new HttpResponse()
        .setUrl("localhost")
        .setTimeout(Duration.ofSeconds(12));

    Map<String, MonitorMetadataPolicyDTO> policies = Map.of(
        "body", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("body")
            .setValueType(MetadataValueType.STRING)
            .setValue("httpbody"),
        "timeout", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("timeout")
            .setValueType(MetadataValueType.DURATION)
            .setValue("PT12S"),
        "followRedirects", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("followRedirects")
            .setValueType(MetadataValueType.BOOL)
            .setValue("true")
    );

    // url is set and has no policy value.  even though it was previously using metadata, it is now excluded
    // body has a different value than the policy, but is now null so will continue to use metadata regardless
    // timeout has the same value as the metadata and was previously set using it, so it remains as metadata
    // any field that was not previously metadata and is now null will be set to metadata
    List<String> fields = MetadataUtils.getMetadataFieldsForUpdate(
        ping, List.of("url", "body", "timeout", "followRedirects"), policies);

    assertThat(fields).hasSize(10);
    assertThat(fields).containsAll(List.of(
        "httpProxy",
        "timeout",
        "followRedirects",
        "body",
        "responseStringMatch",
        "tlsCa",
        "tlsCert",
        "tlsKey",
        "insecureSkipVerify",
        "headers"));
  }

  @Test
  public void setNewMetadataValues_monitor() {
    Monitor monitor = new Monitor();
    List<String> metadataFields = List.of("interval");
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = Map.of("interval",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("interval")
            .setValueType(MetadataValueType.DURATION)
            .setValue("PT12S"));

    MetadataUtils.setNewMetadataValues(monitor, metadataFields, policyMetadata);
    assertThat(monitor.getInterval()).isEqualTo(Duration.ofSeconds(12));
  }

  @Test
  public void setNewMetadataValues_monitor_wrongValueType() {
    Monitor monitor = new Monitor();
    List<String> metadataFields = List.of("interval");
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = Map.of("interval",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("interval")
            .setValueType(MetadataValueType.INT)
            .setValue("12"));

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("Can not set java.time.Duration field "
        + "com.rackspace.salus.telemetry.entities.Monitor.interval to java.lang.Integer");

    MetadataUtils.setNewMetadataValues(monitor, metadataFields, policyMetadata);
  }

  @Test
  public void setNewMetadataValues_monitor_noRelevantMetadata() {
    Monitor monitor = new Monitor();
    List<String> metadataFields = List.of("interval");
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = Map.of("timer",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("interval")
            .setValueType(MetadataValueType.DURATION)
            .setValue("12"));

    MetadataUtils.setNewMetadataValues(monitor, metadataFields, policyMetadata);

    assertThat(monitor.getInterval()).isNull();
  }

  @Test
  public void setNewMetadataValues_plugin() {
    Ping plugin = new Ping();
    List<String> metadataFields = List.of("pingInterval");
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = Map.of("pingInterval",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("pingInterval")
            .setValueType(MetadataValueType.DURATION)
            .setValue("PT67S"));

    MetadataUtils.setNewMetadataValues(plugin, metadataFields, policyMetadata);
    assertThat(plugin.getPingInterval()).isEqualTo(Duration.ofSeconds(67));
  }

  @Test
  public void setUpdateMetadataValue_INT() {
    Ping plugin = new Ping();
    MonitorMetadataPolicyDTO policy = (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
        .setKey("count")
        .setValueType(MetadataValueType.INT)
        .setValue("43");

    MetadataUtils.updateMetadataValue(plugin, policy);
    assertThat(plugin.getCount()).isEqualTo(43);
  }

  @Test
  public void setUpdateMetadataValue_STRING() {
    Disk plugin = new Disk();
    MonitorMetadataPolicyDTO policy = (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
        .setKey("mount")
        .setValueType(MetadataValueType.STRING)
        .setValue("/mymount");

    MetadataUtils.updateMetadataValue(plugin, policy);
    assertThat(plugin.getMount()).isEqualTo("/mymount");
  }

  @Test
  public void setUpdateMetadataValue_DURATION() {
    Monitor monitor = new Monitor();
    MonitorMetadataPolicyDTO policy = (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
        .setKey("interval")
        .setValueType(MetadataValueType.DURATION)
        .setValue("PT44S");

    MetadataUtils.updateMetadataValue(monitor, policy);
    assertThat(monitor.getInterval()).isEqualTo(Duration.ofSeconds(44));
  }

  @Test
  public void setUpdateMetadataValue_STRING_LIST() {
    Monitor monitor = new Monitor();
    MonitorMetadataPolicyDTO policy = (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
        .setKey("zones")
        .setValueType(MetadataValueType.STRING_LIST)
        .setValue("zone1,zone2");

    MetadataUtils.updateMetadataValue(monitor, policy);
    assertThat(monitor.getZones()).isEqualTo(List.of("zone1", "zone2"));
  }

  @Test
  public void setUpdateMetadataValue_BOOL() {
    HttpResponse monitor = new HttpResponse();
    MonitorMetadataPolicyDTO policy = (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
        .setMonitorType(MonitorType.http)
        .setKey("followRedirects")
        .setValueType(MetadataValueType.BOOL)
        .setValue("true");

    MetadataUtils.updateMetadataValue(monitor, policy);
    assertThat(monitor.getFollowRedirects()).isEqualTo(true);
  }

  @Test
  public void setUpdateMetadataValue_BOOLfails() {
    HttpResponse monitor = new HttpResponse();
    MonitorMetadataPolicyDTO policy = (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
        .setKey("followRedirects")
        .setValueType(MetadataValueType.BOOL)
        .setValue("not a boolean value");

    assertThatThrownBy(() -> MetadataUtils.updateMetadataValue(monitor, policy))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("The String did not match either specified value");
  }

  @Test
  public void testSetMetadataFieldsForMonitor() {
    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any(), anyBoolean()))
        .thenReturn(Map.of(
            "interval", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setValueType(MetadataValueType.DURATION)
                .setValue("PT1S")
                .setKey("interval"),
            "monitorName", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setValueType(MetadataValueType.STRING)
                .setValue("default monitor name")
                .setKey("name")
        ));

    String tenantId = RandomStringUtils.randomAlphabetic(10);
    Monitor monitor = new Monitor();
    assertThat(monitor.getMonitorMetadataFields()).isNull();

    metadataUtils.setMetadataFieldsForMonitor(tenantId, monitor, false);
    assertThat(monitor.getMonitorMetadataFields()).hasSize(2);
    assertThat(monitor.getMonitorMetadataFields()).containsExactlyInAnyOrder("monitorName", "interval");

    // set a value different from the policy to remove it from metadata fields
    monitor.setInterval(Duration.ofSeconds(10));
    metadataUtils.setMetadataFieldsForMonitor(tenantId, monitor, false);
    assertThat(monitor.getMonitorMetadataFields()).hasSize(1);
    assertThat(monitor.getMonitorMetadataFields()).containsExactly("monitorName");

    // set a value the same as the policy and it should remain in metadata fields.
    monitor.setMonitorName("default monitor name");
    metadataUtils.setMetadataFieldsForMonitor(tenantId, monitor, false);
    assertThat(monitor.getMonitorMetadataFields()).hasSize(1);

    // set a value different from the policy to remove it from metadata fields
    monitor.setMonitorName("non policy monitor name");
    metadataUtils.setMetadataFieldsForMonitor(tenantId, monitor, false);
    assertThat(monitor.getMonitorMetadataFields()).hasSize(0);

    verify(policyApi, times(4)).getEffectiveMonitorMetadataMap(tenantId, TargetClassName.Monitor, null, true);
  }

  @Test
  public void testGetDefaultZonesForResource_nullRegion() {
    List<String> defaultZones = List.of("public/defaultZone1" ,"public/defaultZone2");
    when(policyApi.getDefaultMonitoringZones(anyString(), anyBoolean()))
        .thenReturn(defaultZones);

    List<String> zones = metadataUtils.getDefaultZonesForResource(null, false);

    assertThat(zones).containsExactlyInAnyOrderElementsOf(defaultZones);
    verify(policyApi).getDefaultMonitoringZones(MetadataPolicy.DEFAULT_ZONE, false);
    verifyNoMoreInteractions(policyApi);
  }

  @Test
  public void testGetDefaultZonesForResource_blankRegion() {
    List<String> defaultZones = List.of("public/defaultZone1" ,"public/defaultZone2");
    when(policyApi.getDefaultMonitoringZones(anyString(), anyBoolean()))
        .thenReturn(defaultZones);

    List<String> zones = metadataUtils.getDefaultZonesForResource("", false);

    assertThat(zones).containsExactlyInAnyOrderElementsOf(defaultZones);
    verify(policyApi).getDefaultMonitoringZones(MetadataPolicy.DEFAULT_ZONE, false);
    verifyNoMoreInteractions(policyApi);
  }

  @Test
  public void testGetDefaultZonesForResource_validRegion() {
    List<String> defaultZones = List.of("public/defaultZone1" ,"public/defaultZone2");

    // first request returns nothing, so the default request will follow after
    when(policyApi.getDefaultMonitoringZones(anyString(), anyBoolean()))
        .thenReturn(Collections.emptyList())
        .thenReturn(defaultZones);

    List<String> zones = metadataUtils.getDefaultZonesForResource("invalidZone", false);

    assertThat(zones).containsExactlyInAnyOrderElementsOf(defaultZones);

    verify(policyApi).getDefaultMonitoringZones("invalidZone", false);
    verify(policyApi).getDefaultMonitoringZones(MetadataPolicy.DEFAULT_ZONE, false);
    verifyNoMoreInteractions(policyApi);
  }

  @Test
  public void testGetDefaultZonesForResource_invalidRegion() {
    List<String> defaultZones = List.of("public/defaultZone1" ,"public/defaultZone2");
    when(policyApi.getDefaultMonitoringZones(anyString(), anyBoolean()))
        .thenReturn(defaultZones);

    List<String> zones = metadataUtils.getDefaultZonesForResource("validZone", false);

    assertThat(zones).containsExactlyInAnyOrderElementsOf(defaultZones);
    verify(policyApi).getDefaultMonitoringZones("validZone", false);
    verifyNoMoreInteractions(policyApi);
  }
}
