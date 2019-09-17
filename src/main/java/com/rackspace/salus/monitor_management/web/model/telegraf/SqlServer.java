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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.ApplicableMonitorType;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.validator.ValidLocalHost;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
@ApplicableAgentType(AgentType.TELEGRAF)
@ApplicableMonitorType(MonitorType.sqlserver)
public class SqlServer extends LocalPlugin {
  public static final String REGEXP = "^(sqlserver://.+)|(([^?;]+=[^;]+;)*([^?;]+=[^;]+);?)$";
  public static final String ERR_MESSAGE = "invalid sqlserver db connection string";
  @NotEmpty
  List<@ValidLocalHost @Pattern(regexp = SqlServer.REGEXP, message = SqlServer.ERR_MESSAGE)String> servers;
  @JsonProperty("query_version")
  @Min(2)
  @Max(2)
  Integer queryVersion = 2;
  boolean azuredb;
  // Jackson seems to exclude serialiation of fields whose names start with 'exclude' unless:
  @JsonProperty("exclude_query")
  List<String> excludeQuery;
}
