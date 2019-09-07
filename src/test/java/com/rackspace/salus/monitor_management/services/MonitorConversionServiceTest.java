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


import com.rackspace.salus.monitor_management.config.MonitorConversionProperties;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.Plugin;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.Plugin;
import com.rackspace.salus.monitor_management.web.model.telegraf.Cpu;
import com.rackspace.salus.monitor_management.web.model.telegraf.DiskIo;
import com.rackspace.salus.monitor_management.web.model.telegraf.HttpResponse;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mem;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.monitor_management.web.model.telegraf.Procstat;
import com.rackspace.salus.monitor_management.web.model.telegraf.X509Cert;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
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
import javax.validation.ConstraintViolation;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {MonitorConversionService.class})
@AutoConfigureJson
public class MonitorConversionServiceTest {

  private static final Duration MIN_INTERVAL = Duration.ofSeconds(10);
  private static final Duration DEFAULT_LOCAL_INTERVAL = Duration.ofSeconds(30);
  private static final Duration DEFAULT_REMOTE_INTERVAL = Duration.ofMinutes(5);

  @Autowired
  MonitorConversionService conversionService;

  @Autowired
  MonitorConversionProperties monitorConversionProperties;

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

    final Plugin plugin = ((LocalMonitorDetails) result.getDetails()).getPlugin();
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
        .setContent(content)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getDetails()).isInstanceOf(LocalMonitorDetails.class);

    final Plugin plugin = ((LocalMonitorDetails) result.getDetails()).getPlugin();
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
    final Plugin plugin = remoteMonitorDetails.getPlugin();
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
        .setLabelSelectorMethod(LabelSelectorMethod.OR)
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(input);

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
    final Plugin plugin = remoteMonitorDetails.getPlugin();
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
    final MonitorCU result = conversionService.convertFromInput(input);

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
    final Plugin plugin = remoteMonitorDetails.getPlugin();
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
    final MonitorCU result = conversionService.convertFromInput(input);

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
    final Plugin plugin = localMonitorDetails.getPlugin();
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
    final MonitorCU result = conversionService.convertFromInput(input);
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
    final MonitorCU result = conversionService.convertFromInput(input);
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
    final MonitorCU result = conversionService.convertFromInput(input);
    assertThat(result.getInterval()).isEqualTo(interval);
  }

  @Test
  public void testConvertFrom_Interval_Explicit_ExactlyMin() {
    final Duration interval = MIN_INTERVAL;

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setInterval(interval);
    final MonitorCU result = conversionService.convertFromInput(input);
    assertThat(result.getInterval()).isEqualTo(interval);
  }

  @Test
  public void testConvertFrom_Interval_Explicit_LessThanMin() {
    final Duration interval = MIN_INTERVAL.minus(Duration.ofSeconds(1));

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setInterval(interval);

    assertThatThrownBy(() -> {
      conversionService.convertFromInput(input);
    })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(String.format("Interval cannot be less than %s", MIN_INTERVAL));
  }

  @Test
  public void testConvertFrom_Interval_DefaultLocal() {
    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setInterval(null);
    final MonitorCU result = conversionService.convertFromInput(input);
    assertThat(result.getInterval()).isEqualTo(DEFAULT_LOCAL_INTERVAL);
  }

  @Test
  public void testConvertFrom_Interval_DefaultRemote() {
    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(new RemoteMonitorDetails().setPlugin(new Ping()))
        .setInterval(null);
    final MonitorCU result = conversionService.convertFromInput(input);
    assertThat(result.getInterval()).isEqualTo(DEFAULT_REMOTE_INTERVAL);
  }
}