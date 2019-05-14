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

import com.rackspace.salus.monitor_management.types.ZoneState;
import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.springframework.boot.convert.DurationUnit;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotBlank;

import java.io.Serializable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "zones", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "name"})})

@Data
public class Zone implements Serializable {
  @Id
  @GeneratedValue
  @Type(type="uuid-char")
  UUID id;

  /**
   * Contains the tenant that owns the private zone or "_PUBLIC_" for public zones.
   */
  @NotBlank
  @Column(name="tenant_id")
  String tenantId;

  /**
   * Contains the unique name/label for the zone.
   * Public zones should have a "public/" prefix and then a trailing region descriptor.
   * e.g. "public/us-central-1"
   */
  @NotBlank
  @Column(name="name")
  String name;

  /**
   * Contains an optional hosting provider of the zone.
   * e.g. Rackspace, Google, Amazon
   */
  @Column(name="provider")
  String provider;

  /**
   * Contains an optional region to more precisely define where the zone is running.
   * e.g. for Rackspace, dfw3 may be used; for Google, europe-west3.
   */
  @Column(name="provider_region")
  String providerRegion;

  /**
   * Defines whether a zone is public or private.
   */
  @NotNull
  @Column(name="is_public")
  boolean isPublic;

  /**
   * Contains an optional list of ipv4 and ipv6 ranges that the pollers in this zone reside in.
   * This can be used to whitelist ranges to allow for remote polling.
   * Entries must be valid CIDR notation.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @Column(name="source_ips")
  List<String> sourceIpAddresses;

  /**
   * Defines whether the zone is available or not.
   * This helps determine whether new monitors can be added to the zone.
   */
  @Enumerated(EnumType.STRING)
  @Column(name="state")
  ZoneState state;

  /**
   * Contains a timeout value which begins to countdown after a poller has disconnected.
   * If the timeout is met, the monitors bound to it will be distributed to other available pollers.
   */
  @DurationUnit(ChronoUnit.SECONDS)
  @Column(name="poller_timeout")
  Duration pollerTimeout = Duration.ofSeconds(120);

  /**
   * Converts the Zone object to a ZoneDTO which is a stripped down version to be used
   * as output in the APIs.
   *
   * @return A ZoneDTO with fields mapped from the Zone.
   */
  public ZoneDTO toDTO() {
    return new ZoneDTO()
        .setName(name)
        .setPollerTimeout(pollerTimeout.getSeconds())
        .setProvider(provider)
        .setProviderRegion(providerRegion)
        .setPublic(isPublic)
        .setSourceIpAddresses(sourceIpAddresses);
  }
}
