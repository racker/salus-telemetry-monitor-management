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

package com.rackspace.salus.monitor_management.services;

import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import java.util.Objects;
import java.util.UUID;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class BoundMonitorMatcher extends BaseMatcher<BoundMonitor> {

  private final UUID monitorId;
  private final String tenantId;
  private final String zoneName;
  private final String resourceId;
  private final String envoyId;
  private final String pollerResourceId;

  public BoundMonitorMatcher(Monitor monitor, String zoneName, String resourceId,
                             String envoyId, String pollerResourceId) {
    this.tenantId = monitor.getTenantId();
    this.monitorId = monitor.getId();
    this.zoneName = zoneName;
    this.resourceId = resourceId;
    this.envoyId = envoyId;
    this.pollerResourceId = pollerResourceId;
  }

  @Override
  public boolean matches(Object o) {
    if (!(o instanceof BoundMonitor)) {
      return false;
    }
    final BoundMonitor boundMonitor = (BoundMonitor) o;
    return
        Objects.equals(boundMonitor.getMonitor().getId(), monitorId) &&
        Objects.equals(boundMonitor.getTenantId(), tenantId) &&
        Objects.equals(boundMonitor.getZoneName(), zoneName) &&
        Objects.equals(boundMonitor.getResourceId(), resourceId) &&
        Objects.equals(boundMonitor.getEnvoyId(), envoyId) &&
        Objects.equals(boundMonitor.getPollerResourceId(), pollerResourceId);
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("BoundMonitor with")
        .appendText(" monitorId=").appendValue(monitorId)
        .appendText(" tenantId=").appendValue(tenantId)
        .appendText(" zoneName=").appendValue(zoneName)
        .appendText(" resourceId=").appendValue(resourceId)
        .appendText(" envoyId=").appendValue(envoyId)
        .appendText(" pollerResourceId=").appendValue(pollerResourceId)
    ;
  }
}
