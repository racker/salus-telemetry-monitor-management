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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.monitor_management.web.model.validator.ValidGoDuration;
import com.rackspace.salus.telemetry.model.AgentType;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
@ApplicableAgentType(AgentType.TELEGRAF)
@JsonInclude(Include.NON_NULL)
public class HttpResponse extends RemotePlugin {
  @NotEmpty
  String address;
  String httpProxy;
  @ValidGoDuration
  String responseTimeout;
  @Pattern(regexp = "GET|PUT|POST|DELETE|HEAD|OPTIONS|PATCH|TRACE", message = "invalid http method")
  String method;
  boolean followRedirects;
  String body;
  String responseStringMatch;
  String tlsCa;
  String tlsCert;
  String tlsKey;
  boolean insecureSkipVerify;
  Map<String, String> headers;
}