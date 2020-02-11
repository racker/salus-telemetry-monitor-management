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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

@RunWith(SpringRunner.class)
@AutoConfigureJson
@SpringBootTest(classes = SchemaService.class)
public class SchemaServiceTest {

  @Autowired
  SchemaService schemaService;

  @Test
  public void testGetMonitorPluginsSchema() {
    final JsonNode schema = schemaService.getMonitorPluginsSchema();

    assertThat(schema).isNotNull();
    assertThat(schema.path("title").asText()).isEqualTo("monitor-plugins-scopes");
    assertThat(schema.path("properties").path("local").path("oneOf").isArray()).isTrue();
    assertThat(ImmutableList.copyOf(schema.path("properties").path("local").path("oneOf").elements()))
        .extracting(node -> node.path("$ref").asText())
        // spot check a known definition reference
        .contains("#/definitions/Cpu");
    assertThat(ImmutableList.copyOf(schema.path("properties").path("remote").path("oneOf").elements()))
        .extracting(node -> node.path("$ref").asText())
        // spot check a known definition reference
        .contains("#/definitions/HttpResponse");
    // spot check a definition
    assertThat(schema.path("definitions").path("Cpu").isObject()).isTrue();
  }
}