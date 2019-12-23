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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.rackspace.salus.common.util.SpringResourceUtils;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@RunWith(SpringRunner.class)
@JsonTest
@Import({MonitorConversionService.class, MetadataUtils.class})
public class X509ConversionTest {
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
  public void convertToOutput_x509() throws IOException {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertToOutput_x509");

    final String content = SpringResourceUtils.readContent(
        "/ConversionTests/MonitorConversionServiceTest_x509.json");

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
    assertThat(x509Plugin.getTarget()).isEqualTo("/etc/ssl/certs/ssl-cert-snakeoil.pem");
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

    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    details.setMonitoringZones(Collections.singletonList("z-1"));
    final X509Cert plugin = new X509Cert();
    plugin.setTarget("/etc/ssl/certs/ssl-cert-snakeoil.pem");
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
    final String content = SpringResourceUtils.readContent(
        "/ConversionTests/MonitorConversionServiceTest_x509.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

}
