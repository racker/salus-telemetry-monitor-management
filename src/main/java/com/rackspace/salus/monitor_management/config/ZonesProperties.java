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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("salus.zones")
@Component
@Data
@Validated
public class ZonesProperties {

  /**
   * When rebalancing calculates the average and standard deviation of assignment counts, this
   * property indicates if Envoy's with zero assignments should be included in that calculation.
   * Exclusion potentially leaves the non-zero Envoys balanced with each other, but the unused
   * Envoys would never get assignments due to lack of outliers.
   */
  boolean rebalanceEvaluateZeroes = true;

  /**
   * When rebalancing, this property indicates how many standard deviations above the average
   * assignment count will be considered over-assigned. Those Envoys that are over-assigned will
   * have bound monitors reassigned to other Envoys in the zone.
   */
  float rebalanceStandardDeviations = 0.2f;
}
