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

import static com.rackspace.salus.test.JsonTestUtils.readContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.monitor_management.web.model.telegraf.DiskIo;
import com.rackspace.salus.monitor_management.web.model.telegraf.HttpResponse;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mem;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.monitor_management.web.model.telegraf.Procstat;
import com.rackspace.salus.monitor_management.web.model.telegraf.X509Cert;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import java.io.IOException;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@RunWith(SpringRunner.class)
@JsonTest
@Import({MonitorConversionService.class})
public class MonitorConversionServiceTest {

  // A timestamp to be used in tests that translates to "1970-01-02T03:46:40Z"
  private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochSecond(100000);

  @Configuration
  public static class TestConfig {

  }

  @Autowired
  MonitorConversionService conversionService;

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
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

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
        .setContent(content)
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

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
        .setContent(content)
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

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
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

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
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

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
            .setCreatedTimestamp(DEFAULT_TIMESTAMP)
            .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

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
        .setResourceId("r-1");
    final MonitorCU result = conversionService.convertFromInput(input);
    assertThat(result.getResourceId()).isEqualTo(input.getResourceId());
  }

  @Test
  public void testConvertTo_ResourceId() {
    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setResourceId("r-1")
        .setId(monitorId)
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);
    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);
    assertThat(result.getResourceId()).isEqualTo(monitor.getResourceId());
  }

  @Test
  public void testConvertTo_LabelSelectorMethod() {
    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setId(monitorId)
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);
    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);
    assertThat(result.getLabelSelectorMethod()).isEqualTo(monitor.getLabelSelectorMethod());
  }

  @Test
  public void testConvertFrom_LabelSelectorMethod() {
    DetailedMonitorInput input = new DetailedMonitorInput()
        .setLabelSelectorMethod(LabelSelectorMethod.OR);
    final MonitorCU result = conversionService.convertFromInput(input);
    assertThat(result.getLabelSelectorMethod()).isEqualTo(input.getLabelSelectorMethod());
  }
}