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

package com.rackspace.salus.monitor_management.config;

import com.rackspace.salus.telemetry.entities.MetadataPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("salus.monitor-content")
@Component
@Data
public class MonitorContentProperties {

  /**
   * Allows configuration of the delimiters used by jmustache for monitor content template
   * rendering. The default avoids conflicting with "{{ }}" used by Insomnia for its templating.
   */
  String placeholderDelimiters = "${ }";

  /**
   * Regex string that can be used the detect the variables set in the monitor's content.
   *
   * If any templated variables are found when using this regex with a Matcher, the variable
   * name without the prefix can be retrieved with <code>matcher.group(1)</code>.
   *
   * If the {@link MonitorContentProperties:placeholderDelimiters} changes, this regex should
   * also change.
   */
  String placeholderRegex = String.format("\\$\\{%s(.+?)}", MetadataPolicy.PREFIX);
}
