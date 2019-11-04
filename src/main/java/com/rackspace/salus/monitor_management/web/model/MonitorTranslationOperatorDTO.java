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

package com.rackspace.salus.monitor_management.web.model;

import com.rackspace.salus.telemetry.entities.MonitorTranslationOperator;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.translators.MonitorTranslator;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
public class MonitorTranslationOperatorDTO {
  UUID id;

  String name;

  AgentType agentType;

  String agentVersions;

  MonitorType monitorType;

  ConfigSelectorScope selectorScope;

  MonitorTranslator translatorSpec;

  public MonitorTranslationOperatorDTO(MonitorTranslationOperator entity) {
    this.id = entity.getId();
    this.name = entity.getName();
    this.agentType = entity.getAgentType();
    this.agentVersions = entity.getAgentVersions();
    this.monitorType = entity.getMonitorType();
    this.selectorScope = entity.getSelectorScope();
    this.translatorSpec = entity.getTranslatorSpec();
  }
}
