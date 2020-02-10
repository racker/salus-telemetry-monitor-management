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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SchemaService {

  private final JsonSchemaGenerator schemaGen;

  @Data
  static class MonitorPluginScopes {
    LocalPlugin local;
    RemotePlugin remote;
  }

  @Autowired
  public SchemaService(ObjectMapper objectMapper) {
    schemaGen = new JsonSchemaGenerator(objectMapper);
  }

  public JsonNode getMonitorPluginsSchema() {
    return schemaGen.generateJsonSchema(MonitorPluginScopes.class, "monitor-plugins-scopes",
        "Conveys monitor scopes and plugin definitions for each");
  }
}
