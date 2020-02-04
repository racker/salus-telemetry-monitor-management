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
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.monitor_management.web.model.SummaryField;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.time.Duration;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = false)
@ApplicableAgentType(AgentType.TELEGRAF)
@ApplicableMonitorType(MonitorType.http)
public class HttpResponse extends RemotePlugin {
  @SummaryField
  @NotEmpty
  String url;
  String httpProxy;
  Duration timeout;
  @SummaryField
  @Pattern(regexp = "GET|PUT|POST|DELETE|HEAD|OPTIONS|PATCH|TRACE", message = "invalid http method")
  String method;
  boolean followRedirects;
  String body;
  String responseStringMatch;
  String tlsCa;
  String tlsCert;
  String tlsKey;
  Boolean insecureSkipVerify;
  Map<String, String> headers;
}
