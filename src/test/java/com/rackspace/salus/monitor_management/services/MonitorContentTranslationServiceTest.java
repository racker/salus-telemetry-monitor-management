/*
 * Copyright 2029 Rackspace US, Inc.
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
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationOperatorCreate;
import com.rackspace.salus.telemetry.entities.MonitorTranslationOperator;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.repositories.MonitorTranslationOperatorRepository;
import com.rackspace.salus.telemetry.translators.RenameFieldKeyTranslator;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
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
@AutoConfigureDataJpa
@AutoConfigureTestDatabase
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
        .setTranslatorSpec(new RenameFieldKeyTranslator().setFrom("from").setTo("to"));

    MonitorTranslationOperator op = service.create(create);

    assertThat(op.getId()).isNotNull();
    assertThat(op).hasNoNullFieldsOrProperties();
    assertThat(op).isEqualToIgnoringGivenFields(create, "id");
  }

}