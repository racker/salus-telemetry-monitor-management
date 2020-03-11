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
import com.rackspace.salus.telemetry.translators.MonitorTranslator;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MonitorTranslationOperatorCreate {
  @NotNull
  AgentType agentType;

  @NotBlank
  String name;

  /**
   * Optional field used to add a more detailed description to the operation.
   * Can be used to document why the translation is required.
   */
  String description;

  /**
   * Optional field that conveys applicable version ranges
   * <a href="https://github.com/zafarkhaja/jsemver#external-dsl"></a>in the form of jsemver's external DSL</a>
   */
  String agentVersions;

  /**
   * Optional field, when set, indicates that the translation should only apply to monitors of this type.
   */
  @NotNull
  MonitorType monitorType;

  /**
   * Optional field, when set, indicates that the translation should only appply to monitors with matching scope.
   * Useful for cases where a monitor type is used for both local/agent and remote monitoring, but
   * the translation differs for each case.
   */
  ConfigSelectorScope selectorScope;

  @NotNull @Valid
  MonitorTranslator translatorSpec;

  int order;
}
