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
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("salus.test-monitor")
@Component
@Data
@Validated
public class TestMonitorProperties {

  /**
   * If <code>timeout</code> of {@link com.rackspace.salus.monitor_management.web.model.TestMonitorInput}
   * is not specified, this is the default timeout to use.
   */
  @DurationUnit(ChronoUnit.SECONDS)
  @NotNull
  Duration defaultTimeout = Duration.ofSeconds(30);

  /**
   * This is the duration to add to the test-monitor's timeout to determine the limit of
   * end-to-end correlation time.
   */
  @DurationUnit(ChronoUnit.SECONDS)
  @NotNull
  Duration endToEndTimeoutExtension = Duration.ofSeconds(30);
}
