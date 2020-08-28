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

package com.rackspace.salus.monitor_management.web.model;

import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class TranslateMonitorContentRequest {
  @NotBlank(groups = {ValidationGroups.TranslationWithoutMonitorProvided.class})
  String content;
  @NotNull
  AgentType agentType;
  @NotBlank
  String agentVersion;
  @NotNull(groups = {ValidationGroups.TranslationWithoutMonitorProvided.class})
  MonitorType monitorType;
  @NotNull(groups = {ValidationGroups.TranslationWithoutMonitorProvided.class})
  ConfigSelectorScope scope;
}
