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
package com.rackspace.salus.monitor_management.services;

import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.errors.ZoneDeletionNotAllowed;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.repositories.ZoneRepository;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePrivate;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePublic;
import com.rackspace.salus.monitor_management.web.model.ZoneUpdate;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.model.NotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ZoneManagement {
    private final ZoneRepository zoneRepository;
    private final ZoneStorage zoneStorage;
    private final MonitorRepository monitorRepository;

    @Autowired
    public ZoneManagement(ZoneRepository zoneRepository, ZoneStorage zoneStorage, MonitorRepository monitorRepository) {
      this.zoneRepository = zoneRepository;
      this.zoneStorage = zoneStorage;
      this.monitorRepository = monitorRepository;
    }

  /**
   * Retrieves the zone for the given tenant id and zone name.
   * @param tenantId The tenantId the zone is stored under.
   * @param name The name of the zone.
   * @return A zone if it exists.
   */
    private Optional<Zone> getZone(String tenantId, String name) {
      return zoneRepository.findByTenantIdAndName(tenantId, name);
    }

  /**
   * Helper method to get a public zone by name.
   * @param name The name of the zone.
   * @return A zone if it exists.
   */
    public Optional<Zone> getPublicZone(String name) {
      return getZone(ResolvedZone.PUBLIC, name);
    }

  /**
   * Helper method to get a private zone by name and tenant.
   * @param tenantId The tenantId the zone is stored under.
   * @param name The name of the zone.
   * @return A zone if it exists.
   */
    public Optional<Zone> getPrivateZone(String tenantId, String name) {
      return getZone(tenantId, name);
    }

    /**
     * Store a new private zone in the database.
     * @param tenantId The tenant to create the zone for.
     * @param newZone The zone parameters to store.
     * @return The newly created resource.
     * @throws com.rackspace.salus.telemetry.errors.AlreadyExistsException If the zone name already exists for the tenant.
     */
    public Zone createPrivateZone(String tenantId, @Valid ZoneCreatePrivate newZone) throws AlreadyExistsException {
        if (exists(tenantId, newZone.getName())) {
            throw new AlreadyExistsException(String.format("Zone already exists with name %s on tenant %s",
                    newZone.getName(), tenantId));
        }

        Zone zone = new Zone()
            .setTenantId(tenantId)
            .setName(newZone.getName())
            .setProvider(newZone.getProvider())
            .setProviderRegion(newZone.getProviderRegion())
            .setSourceIpAddresses(newZone.getSourceIpAddresses())
            .setPollerTimeout(Duration.ofSeconds(newZone.getPollerTimeout()))
            .setState(newZone.getState())
            .setPublic(false);

        zoneRepository.save(zone);

        return zone;
    }

  /**
   * Store a new public zone in the database.
   * @param newZone The zone parameters to store.
   * @return The newly created resource.
   * @throws AlreadyExistsException If the zone name already exists for the tenant.
   */
  public Zone createPublicZone(@Valid ZoneCreatePublic newZone) throws AlreadyExistsException {
    if (exists(ResolvedZone.PUBLIC, newZone.getName())) {
      throw new AlreadyExistsException(String.format("Public zone already exists with name %s",
          newZone.getName()));
    }

    Zone zone = new Zone()
        .setTenantId(ResolvedZone.PUBLIC)
        .setName(newZone.getName())
        .setProvider(newZone.getProvider())
        .setProviderRegion(newZone.getProviderRegion())
        .setSourceIpAddresses(newZone.getSourceIpAddresses())
        .setPollerTimeout(Duration.ofSeconds(newZone.getPollerTimeout()))
        .setState(newZone.getState())
        .setPublic(true);

    zoneRepository.save(zone);

    return zone;
  }

    /**
     * Modify the fields of a public or private zone if it exists.
     *
     * @param zone The stored Zone object to update.
     * @param updatedZone The zone parameters to update.
     * @return
     */
    private Zone updateZone(Zone zone, @Valid ZoneUpdate updatedZone) {
      PropertyMapper map = PropertyMapper.get();
      map.from(updatedZone.getProvider())
          .whenNonNull()
          .to(zone::setProvider);
      map.from(updatedZone.getProviderRegion())
          .whenNonNull()
          .to(zone::setProviderRegion);
      map.from(updatedZone.getSourceIpAddresses())
          .whenNonNull()
          .to(zone::setSourceIpAddresses);
      map.from(updatedZone.getPollerTimeout())
          .whenNonNull()
          .to(timeout -> zone.setPollerTimeout(Duration.ofSeconds(updatedZone.getPollerTimeout())));
      map.from(updatedZone.getState())
          .whenNonNull()
          .to(zone::setState);

      zoneRepository.save(zone);

      return zone;
    }

  /**
   * Helper method to update a private zone's details.
   * @param tenantId The tenant of the zone.
   * @param name The name of the zone.
   * @param updatedZone The zone parameters to update.
   * @return The newly updated zone.
   */
  public Zone updatePrivateZone(String tenantId, String name, @Valid ZoneUpdate updatedZone) {
    Zone zone = getPrivateZone(tenantId, name).orElseThrow(() ->
        new NotFoundException(String.format("No zone found named %s on tenant %s",
            name, tenantId)));
    return updateZone(zone, updatedZone);
  }

  /**
   * Helper method to update a public zone's details.
   * @param name The name of the zone.
   * @param updatedZone The zone parameters to update.
   * @return The newly updated zone.
   */
  public Zone updatePublicZone(String name, @Valid ZoneUpdate updatedZone) {
    Zone zone = getPublicZone(name).orElseThrow(() ->
        new NotFoundException(String.format("No public zone found named %s", name)));
    return updateZone(zone, updatedZone);
  }

  /**
   * Deletes the zone from the database if it has no active envoys connected.
   * @param zone The zone to remove.
   */
  private void removeZone(Zone zone) {
    long activeEnvoys = getActiveEnvoyCountForZone(zone);
    log.debug("Found {} active envoys for zone {}", activeEnvoys, zone.getName());
    if (activeEnvoys > 0) {
        throw new ZoneDeletionNotAllowed(
                String.format("Cannot remove zone with connected pollers. Found %d.", activeEnvoys));
    }

    zoneRepository.deleteById(zone.getId());

    // TBD: remove expected entries in etcd?
  }


    /**
     * Helper method to delete private zones by tenant id and zone name.
     * @param tenantId The tenantId the zone is stored under.
     * @param name The name field of the zone.
     * @throws NotFoundException
     * @throws ZoneDeletionNotAllowed
     */
    public void removePrivateZone(String tenantId, String name)
        throws NotFoundException, ZoneDeletionNotAllowed {
      Zone zone = getPrivateZone(tenantId, name).orElseThrow(() ->
          new NotFoundException(String.format("No zone found named %s on tenant %s",
              name, tenantId)));

      int monitors = getMonitorCountForPrivateZone(tenantId, name);
      if(monitors > 0) {
        throw new ZoneDeletionNotAllowed(
            String.format("Cannot remove zone with configured monitors. Found %s.", monitors));
      }
      removeZone(zone);
    }

    /**
     * Helper method to remove public zones by zone name.
     * @param name The name of the zone.
     * @throws NotFoundException
     * @throws ZoneDeletionNotAllowed
     */
    public void removePublicZone(String name)
        throws NotFoundException, ZoneDeletionNotAllowed {
      Zone zone = getPublicZone(name).orElseThrow(() ->
          new NotFoundException(String.format("No public zone found named %s", name)));

      int monitors = getMonitorCountForPublicZone(name);
      if(monitors > 0) {
        throw new ZoneDeletionNotAllowed(
            String.format("Cannot remove zone with configured monitors. Found %s.", monitors));
      }
      removeZone(zone);
    }

    private long getActiveEnvoyCountForZone(Zone zone) {
        ResolvedZone resolvedZone;
        if (zone.getTenantId().equals(ResolvedZone.PUBLIC)) {
            resolvedZone = ResolvedZone.createPublicZone(zone.getName());
        } else {
            resolvedZone = ResolvedZone.createPrivateZone(zone.getTenantId(), zone.getName());
        }

        return zoneStorage.getActiveEnvoyCountForZone(resolvedZone).join();
    }

    private List<Zone> getZonesByTenant(String tenantId) {
        return zoneRepository.findAllByTenantId(tenantId);
    }

    private List<Zone> getAllPublicZones() {
        return getZonesByTenant(ResolvedZone.PUBLIC);
    }

    public List<Zone> getAvailableZonesForTenant(String tenantId) {
        List<Zone> availableZones = new ArrayList<>();
        availableZones.addAll(getAllPublicZones());
        availableZones.addAll(getZonesByTenant(tenantId));

        return availableZones;
    }

    public List<Monitor> getMonitorsForZone(String tenantId, String zone) {
        return monitorRepository.findByTenantIdAndZonesContains(tenantId, zone);
    }

  /**
   * Get the number of monitors for a zone across all tenants.
   * This should be used when looking up public zones.
   *
   * @param zoneName The zone to lookup.
   * @return The count of monitors in the zone.
   */
  int getMonitorCountForPublicZone(String zoneName) {
    return monitorRepository.countAllByZonesContains(zoneName);
  }

  /**
   * Get the number of monitors for a zone on a single tenant.
   * This should be used when looking up private zones.
   *
   * @param tenantId The tenant the zone resides on.
   * @param zoneName The zone to lookup.
   * @return The count of monitors in the zone.
   */
  int getMonitorCountForPrivateZone(String tenantId, String zoneName) {
    return monitorRepository.countAllByTenantIdAndZonesContains(tenantId, zoneName);
  }



    /**
     * Tests whether the zone exists on the given tenant.
     * @param tenantId The tenant owning the zone.
     * @param zoneName The unique value representing the zone.
     * @return True if the zone exists on the tenant, otherwise false.
     */
    public boolean exists(String tenantId, String zoneName) {
        return zoneRepository.existsByTenantIdAndName(tenantId, zoneName);
    }

    /**
     * Tests whether the public zone exists.
     * @param zoneName The unique value representing the zone.
     * @return True if the zone exists, otherwise false.
     */
    public boolean publicZoneExists(String zoneName) {
        return exists(ResolvedZone.PUBLIC, zoneName);
    }
}
