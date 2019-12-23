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

import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.ApplicableMonitorType;
import com.rackspace.salus.monitor_management.web.model.Protocol;
import com.rackspace.salus.monitor_management.web.model.RecordType;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.monitor_management.web.model.validator.ValidGoDuration;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.MonitorType;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = false)
@ApplicableAgentType(AgentType.TELEGRAF)
@ApplicableMonitorType(MonitorType.dns)
public class Dns extends RemotePlugin {
  @NotBlank
  String dnsServer;
  @NotBlank
  String domain;
  @NotNull
  Protocol network = Protocol.udp;
  @NotNull
  RecordType recordType = RecordType.A;
  @NotNull
  int port = 53;
  @ValidGoDuration
  String timeout;
}