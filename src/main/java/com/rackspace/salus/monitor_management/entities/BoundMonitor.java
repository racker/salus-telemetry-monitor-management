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

package com.rackspace.salus.monitor_management.entities;

import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import java.io.Serializable;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import lombok.Data;

// Using the old validation exceptions for podam support
// Will move to the newer ones once they're supported.
//import javax.validation.constraints.NotBlank;

/**
 * This entity tracks the three-way binding of
 * <ul>
 *   <li>monitor</li>
 *   <li>resource</li>
 *   <li>zone (empty string for local/agent monitors)</li>
 * </ul>
 *
 * <p>
 * As part of the binding, the agent configuration content of the monitor is stored in its
 * rendered form with all context placeholders replaced with their per-binding values.
 * </p>
 *
 * <p>
 *   This entity has a corresponding DTO at
 *   {@link com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO}
 *   where the fields of that class must be maintained to align with the fields of this entity.
 * </p>
 */
@Entity
@IdClass(BoundMonitor.PrimaryKey.class)
@Table(name = "bound_monitors",
indexes = {
    @Index(name = "by_envoy_id", columnList = "envoyId"),
    @Index(name = "by_zone_envoy", columnList = "zoneTenantId,zoneId,envoyId"),
    @Index(name = "by_resource", columnList = "resourceId")
})
@Data
public class BoundMonitor implements Serializable {

  @Data
  public static class PrimaryKey implements Serializable {

    /**
     * The Java and Hibernate type of <code>monitor</code> needs to match the primary key
     * of {@link Monitor}
     */
    @org.hibernate.annotations.Type(type="uuid-char")
    UUID monitor;
    String resourceId;
    String zoneId;

    // zoneTenantId and resourceTenant do not need to be part of the primary key
    // since the Monitor, via monitorId, already scopes this binding to a tenant
  }

  @Id
  @ManyToOne
  Monitor monitor;

  /**
   * Contains the tenant that owns the private zone or {@value com.rackspace.salus.telemetry.etcd.types.ResolvedZone#PUBLIC} for public zones.
   */
  @NotNull
  @Column(length = 100)
  String zoneTenantId;

  /**
   * For remote monitors, contains the binding of a specific monitoring zone.
   * For local monitors, this field is an empty string.
   */
  @Id
  @NotNull
  @Column(length = 100)
  String zoneId;

  /**
   * Contains the binding of the {@link Monitor} (via <code>monitorId</code>) to a specific
   * resource to be monitored.
   */
  @Id
  @NotNull
  @Column(length = 100)
  String resourceId;

  @Lob
  String renderedContent;

  @Column(length = 100)
  String envoyId;

  public BoundMonitorDTO toDTO() {
    return new BoundMonitorDTO()
        .setMonitorId(monitor.getId())
        .setZoneId(zoneId)
        .setResourceTenant(monitor.getTenantId())
        .setResourceId(resourceId)
        .setSelectorScope(monitor.getSelectorScope())
        .setAgentType(monitor.getAgentType())
        .setRenderedContent(renderedContent)
        .setEnvoyId(envoyId);
  }
}