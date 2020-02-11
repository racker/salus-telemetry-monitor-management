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

package com.rackspace.salus.monitor_management.web.controller;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.rackspace.salus.monitor_management.services.SchemaService;
import com.rackspace.salus.telemetry.model.View;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/schema/monitor")
public class MonitorSchemaController {
  private final SchemaService schemaService;

  @Autowired
  public MonitorSchemaController(SchemaService schemaService) {
    this.schemaService = schemaService;
  }

  @GetMapping("/plugins")
  @JsonView(View.Public.class)
  public JsonNode getMonitorPluginsSchema() {
    return schemaService.getMonitorPluginsSchema();
  }

}
