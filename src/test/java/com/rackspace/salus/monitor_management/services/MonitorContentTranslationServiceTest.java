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

package com.rackspace.salus.monitor_management.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationOperatorCreate;
import com.rackspace.salus.telemetry.entities.MonitorTranslationOperator;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.repositories.MonitorTranslationOperatorRepository;
import com.rackspace.salus.telemetry.translators.GoDurationTranslator;
import com.rackspace.salus.telemetry.translators.RenameFieldKeyTranslator;
import com.rackspace.salus.telemetry.translators.ScalarToArrayTranslator;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    MonitorContentTranslationService.class,
    MeterRegistryTestConfig.class,
    DatabaseConfig.class,
    ObjectMapper.class
})
@EnableTestContainersDatabase
@AutoConfigureDataJpa
@AutoConfigureTestEntityManager
public class MonitorContentTranslationServiceTest {

  @Autowired
  MonitorContentTranslationService service;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MonitorTranslationOperatorRepository repository;

  @Autowired
  TestEntityManager entityManager;

  @Test
  public void testCreate_noNullFields() {
    final MonitorTranslationOperatorCreate create = new MonitorTranslationOperatorCreate()
        .setName(RandomStringUtils.randomAlphabetic(10))
        .setDescription(RandomStringUtils.randomAlphabetic(40))
        .setAgentType(AgentType.TELEGRAF)
        .setAgentVersions(">= 1.12.0")
        .setMonitorType(MonitorType.dns)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setTranslatorSpec(new RenameFieldKeyTranslator().setFrom("from").setTo("to"))
        .setOrder(5);

    MonitorTranslationOperator op = service.create(create);

    assertThat(op.getId()).isNotNull();
    assertThat(op).hasNoNullFieldsOrProperties();
    assertThat(op).isEqualToIgnoringGivenFields(create, "id");
  }

  @Test
  public void testGetMonitorTranslationDetails() {
    createTranslations();
    List<MonitorTranslationDetails> details = service.getMonitorTranslationDetails();

    assertThat(details).hasSize(4);
    assertThat(details).containsExactlyInAnyOrder(
        new MonitorTranslationDetails()
            .setMonitorType(MonitorType.oracle_rman)
            .setAgentType(AgentType.ORACLE)
            .setTranslations(List.of("The key 'from-field' is renamed to 'to-field'")),
        new MonitorTranslationDetails()
            .setMonitorType(MonitorType.cpu)
            .setAgentType(AgentType.TELEGRAF)
            .setTranslations(List.of("The key 'from-field' is renamed to 'to-field'")),
        new MonitorTranslationDetails()
            .setMonitorType(MonitorType.http)
            .setAgentType(AgentType.TELEGRAF)
            .setTranslations(List.of(
                "'url' becomes the singleton array named 'urls'",
                "'timeout' becomes a Golang formatted duration",
                "The key 'timeout' is renamed to 'responseTimeout'")),
        new MonitorTranslationDetails()
            .setMonitorType(MonitorType.ssl)
            .setAgentType(AgentType.TELEGRAF)
            .setTranslations(List.of("'target' becomes the singleton array named 'sources'")));
  }

  private void createTranslations() {
    List<MonitorTranslationOperator> operators = List.of(
        new MonitorTranslationOperator()
            .setName("cpu-rename")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(">= 1.12.0")
            .setMonitorType(MonitorType.cpu)
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setTranslatorSpec(new RenameFieldKeyTranslator()
                .setFrom("from-field")
                .setTo("to-field")
            )
            .setOrder(0),
        new MonitorTranslationOperator()
            .setName("http-rename-timeout")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setMonitorType(MonitorType.http)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setTranslatorSpec(new RenameFieldKeyTranslator()
                .setFrom("timeout")
                .setTo("responseTimeout")
            )
            .setOrder(2),
        new MonitorTranslationOperator()
            .setName("http-go-timeout")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setMonitorType(MonitorType.http)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setTranslatorSpec(new GoDurationTranslator()
                .setField("timeout")
            )
            .setOrder(1),
        new MonitorTranslationOperator()
            .setName("http-url-list")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setMonitorType(MonitorType.http)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setTranslatorSpec(new ScalarToArrayTranslator()
                .setFrom("url")
                .setTo("urls")
            )
            .setOrder(0),
        new MonitorTranslationOperator()
            .setName("ssl-local-sources-list")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setMonitorType(MonitorType.ssl)
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setTranslatorSpec(new ScalarToArrayTranslator()
                .setFrom("target")
                .setTo("sources")
            )
            .setOrder(2),
        new MonitorTranslationOperator()
            .setName("oracle-rename")
            .setAgentType(AgentType.ORACLE)
            .setAgentVersions(null)
            .setMonitorType(MonitorType.oracle_rman)
            .setSelectorScope(null)
            .setTranslatorSpec(new RenameFieldKeyTranslator()
                .setFrom("from-field")
                .setTo("to-field")
            )
            .setOrder(0));

    repository.saveAll(operators);
  }
}