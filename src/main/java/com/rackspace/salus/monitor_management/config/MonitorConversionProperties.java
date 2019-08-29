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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("salus.monitor-conversion")
@Data
public class MonitorConversionProperties {

  /**
   * Monitors are not allowed to be created/updated with an interval less than this value.
   */
  @DurationUnit(ChronoUnit.SECONDS)
  Duration minimumAllowedInterval = Duration.ofSeconds(30);

  /**
   * This is the default value used if a create or update API call provides a local monitor
   * without <code>interval</code> set.
   */
  @DurationUnit(ChronoUnit.SECONDS)
  Duration defaultLocalInterval = Duration.ofSeconds(60);

  /**
   * This is the default value used if a create or update API call provides a remote monitor
   * without <code>interval</code> set.
   */
  @DurationUnit(ChronoUnit.SECONDS)
  Duration defaultRemoteInterval = Duration.ofSeconds(60);
}
