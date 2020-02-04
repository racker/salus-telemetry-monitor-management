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

package com.rackspace.salus.monitor_management.web.model.telegraf;

import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.ApplicableMonitorType;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.SummaryField;
import com.rackspace.salus.monitor_management.web.model.validator.ValidLocalHost;
import com.rackspace.salus.monitor_management.web.validator.PostgresqlValidator.AtMostOneOf;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.time.Duration;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = false)
@ApplicableAgentType(AgentType.TELEGRAF)
@ApplicableMonitorType(MonitorType.postgresql)
@AtMostOneOf
public class Postgresql extends LocalPlugin {
  public static final String REGEXP = "^(postgres://.+)|(([^ ]+=[^ ]+ )*([^ ]+=[^ ]+))$";
  public static final String ERR_MESSAGE = "invalid postgresql db connection string";
  @SummaryField
  @NotEmpty
  @ValidLocalHost
  @Pattern(regexp = Postgresql.REGEXP, message = Postgresql.ERR_MESSAGE)
  String address;
  String outputaddress;
  Duration maxLifetime;
  List<String> ignoredDatabases;
  List<String> databases;
}
