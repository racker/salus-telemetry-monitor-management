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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.ApplicableMonitorType;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.validator.ValidGoDuration;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import com.rackspace.salus.monitor_management.web.model.validator.ValidLocalHost;

@Data @EqualsAndHashCode(callSuper = true)
@ApplicableAgentType(AgentType.TELEGRAF)
@ApplicableMonitorType(MonitorType.sqlserver)
public class SqlServer extends LocalPlugin {
  @NotEmpty
  List<@ValidLocalHost String> servers;
  @JsonProperty("query_version")
  @Min(2)
  @Max(2)
  Integer queryVersion = 2;
  boolean azuredb;
  @JsonProperty("exclude_query")
  List<String> excludeQuery;
}
