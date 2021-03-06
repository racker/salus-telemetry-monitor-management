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
 *
 */
package com.rackspace.salus.monitor_management.web.controller;

import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.services.ZoneManagement;
import com.rackspace.salus.monitor_management.web.model.MonitorDTO;
import com.rackspace.salus.monitor_management.web.model.RebalanceResult;
import com.rackspace.salus.monitor_management.web.model.ZoneAssignmentCount;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePrivate;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePublic;
import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import com.rackspace.salus.monitor_management.web.model.ZoneUpdate;
import com.rackspace.salus.telemetry.entities.Zone;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.PrivateZoneName;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.PagedContent;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@RestController
@RequestMapping("/api")
@Api(description = "Zone operations", authorizations = {
    @Authorization(value = "repose_auth",
        scopes = {
            @AuthorizationScope(scope = "write:zone", description = "modify Zones in your account"),
            @AuthorizationScope(scope = "read:zone", description = "read your Zones"),
            @AuthorizationScope(scope = "delete:zone", description = "delete your Zones")
        })
})
public class ZoneApiController {

  private ZoneManagement zoneManagement;
  private final MonitorManagement monitorManagement;
  private ZoneStorage zoneStorage;

  public ZoneApiController(ZoneManagement zoneManagement, MonitorManagement monitorManagement,
      ZoneStorage zoneStorage) {
    this.zoneManagement = zoneManagement;
    this.monitorManagement = monitorManagement;
    this.zoneStorage = zoneStorage;
  }

  @GetMapping("/tenant/{tenantId}/zones/**")
  @ApiOperation(value = "Gets specific zone by tenant id and zone name")
  public ZoneDTO getAvailableZone(@PathVariable String tenantId, HttpServletRequest request) {
    String name = extractZoneNameFromUri(request);
    return getByZoneName(tenantId, name);
  }

  public ZoneDTO getByZoneName(String tenantId, String name) {
    Optional<Zone> zone;
    if (name.startsWith(ResolvedZone.PUBLIC_PREFIX)) {
      zone = zoneManagement.getPublicZone(name);
    } else {
      zone = zoneManagement.getPrivateZone(tenantId, name);
    }
    return new ZoneDTO(zone
        .orElseThrow(() -> new NotFoundException(String.format("No zone found named %s", name))));
  }

  @GetMapping("/admin/zones/**")
  @ApiOperation(value = "Gets specific public zone by name")
  public ZoneDTO getPublicZone(HttpServletRequest request) {
    String name = extractZoneNameFromUri(request);
    return getByZoneName(null, name);
  }

  /**
   * Discovers the zone name within the uri.
   * Handles both public zones containing slashes as well as more simple private zone.
   * Using @PathVariable does not work for public zones.
   * @param request The incoming http request.
   * @return The zone name provided within the uri.
   */
  private String extractZoneNameFromUri(HttpServletRequest request){
    // For example, /api/admin//zones/public/region_1
    String path = (String) request
        .getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    // For example, /api/admin/zones/**
    String bestMatchPattern = (String) request
        .getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    // For example, public/region_1
    return new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);
  }

  /**
   * Helper method that calls {@link #extractZoneNameFromUri(HttpServletRequest)}
   * but also validates the zone name is a legal public zone name.
   * @param request The incoming http request.
   * @return The zone name provided within the uri.
   */
  private String extractPublicZoneNameFromUri(HttpServletRequest request) {
    final String name = extractZoneNameFromUri(request);
    if (!name.startsWith(ResolvedZone.PUBLIC_PREFIX)) {
      throw new IllegalArgumentException("Must provide a public zone name");
    }
    return name;
  }

  @GetMapping("/tenant/{tenantId}/zone-assignment-counts/{name}")
  @ApiOperation(value = "Gets assignment counts of monitors to poller-envoys in the private zone")
  public CompletableFuture<List<ZoneAssignmentCount>> getPrivateZoneAssignmentCounts(
      @PathVariable String tenantId, @PathVariable @PrivateZoneName String name) {

    if (!zoneManagement.exists(tenantId, name)) {
      throw new NotFoundException(String.format("No private zone found named %s", name));
    }

    return monitorManagement.getZoneAssignmentCounts(tenantId, name);
  }

  @GetMapping("/tenant/{tenantId}/zone-assignment-counts")
  @ApiOperation(value = "Gets assignment counts of monitors to poller-envoys in all the private zones of a tenant")
  public CompletableFuture<Map<String, List<ZoneAssignmentCount>>> getPrivateZoneAssignmentCountsPerTenant(
      @PathVariable String tenantId) {

    return monitorManagement.getZoneAssignmentCountForTenant(tenantId);
  }

  @GetMapping("/admin/zone-assignment-counts/**")
  @ApiOperation(value = "Gets assignment counts of monitors to poller-envoys in the public zone")
  public CompletableFuture<List<ZoneAssignmentCount>> getPublicZoneAssignmentCounts(
      HttpServletRequest request) {

    String name = extractPublicZoneNameFromUri(request);

    if (!zoneManagement.publicZoneExists(name)) {
      throw new NotFoundException(String.format("No public zone found named %s", name));
    }

    return monitorManagement.getZoneAssignmentCounts(null, name);
  }

  @GetMapping("/admin/zone-assignment-counts")
  @ApiOperation(value = "Gets assignment counts of monitors to poller-envoys for all the public zones")
  public CompletableFuture<Map<String, List<ZoneAssignmentCount>>> getPublicZoneAssignmentCountsPerTenant() {
    return monitorManagement.getZoneAssignmentCountForTenant(ResolvedZone.PUBLIC);
  }

  @PostMapping("/tenant/{tenantId}/zones")
  @ResponseStatus(HttpStatus.CREATED)
  @ApiOperation(value = "Creates a new private zone for the tenant")
  public ZoneDTO create(@PathVariable String tenantId, @Valid @RequestBody ZoneCreatePrivate zone)
          throws AlreadyExistsException {
    return new ZoneDTO(zoneManagement.createPrivateZone(tenantId, zone));
  }

  @PostMapping("/admin/zones")
  @ResponseStatus(HttpStatus.CREATED)
  @ApiOperation(value = "Creates a new public zone")
  public ZoneDTO create(@Valid @RequestBody ZoneCreatePublic zone)
      throws AlreadyExistsException {
    return new ZoneDTO(zoneManagement.createPublicZone(zone));
  }

  @PutMapping("/tenant/{tenantId}/zones/{name}")
  @ApiOperation(value = "Updates a specific private zone for the tenant")
  public ZoneDTO update(@PathVariable String tenantId, @PathVariable String name, @Valid @RequestBody ZoneUpdate zone) {
    return new ZoneDTO(zoneManagement.updatePrivateZone(tenantId, name, zone));
  }

  @PutMapping("/admin/zones/**")
  @ApiOperation(value = "Updates a specific public zone")
  public ZoneDTO update(HttpServletRequest request, @Valid @RequestBody ZoneUpdate zone) {
    String name = extractPublicZoneNameFromUri(request);
    return new ZoneDTO(zoneManagement.updatePublicZone(name, zone));
  }

  @PostMapping("/tenant/{tenantId}/rebalance-zone/{name}")
  @ApiOperation(value = "Rebalances a private zone")
  public CompletableFuture<RebalanceResult> rebalancePrivateZone(@PathVariable String tenantId,
                                                                 @PathVariable @PrivateZoneName String name) {
    if (!zoneManagement.exists(tenantId, name)) {
      throw new NotFoundException(String.format("No private zone found named %s", name));
    }

    return monitorManagement.rebalanceZone(tenantId, name)
        .thenApply(reassigned -> new RebalanceResult().setReassigned(reassigned));
  }

  @PostMapping("/admin/rebalance-zone/**")
  @ApiOperation(value = "Rebalances a public zone")
  public CompletableFuture<RebalanceResult> rebalancePublicZone(HttpServletRequest request) {
    String name = extractPublicZoneNameFromUri(request);

    if (!zoneManagement.publicZoneExists(name)) {
      throw new NotFoundException(String.format("No public zone found named %s", name));
    }

    return monitorManagement.rebalanceZone(null, name)
        .thenApply(reassigned -> new RebalanceResult().setReassigned(reassigned));
  }

  @DeleteMapping("/tenant/{tenantId}/zones/{name}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiOperation(value = "Deletes a specific private zone for the tenant")
  public void delete(@PathVariable String tenantId,
                     @PathVariable @PrivateZoneName String name) {
    zoneManagement.removePrivateZone(tenantId, name);
  }

  @DeleteMapping("/admin/zones/**")
  @ApiOperation(value = "Deletes a specific public zone")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpServletRequest request) {
    String name = extractPublicZoneNameFromUri(request);
    zoneManagement.removePublicZone(name);
  }

  @GetMapping("/tenant/{tenantId}/zones")
  @ApiOperation(value = "Gets all zones available to be used in the tenant's monitor configurations")
  public PagedContent<ZoneDTO> getAvailableZones(@PathVariable String tenantId, Pageable pageable) {
    return PagedContent.fromPage(
        zoneManagement.getAvailableZonesForTenant(tenantId, pageable)
        .map(ZoneDTO::new));
  }

  @GetMapping("/admin/zones")
  @ApiOperation(value = "Gets all public zones")
  public PagedContent<ZoneDTO> getAllPublicZones(Pageable pageable) {
    return PagedContent.fromPage(
        zoneManagement.getAllPublicZones(pageable)
        .map(ZoneDTO::new));
  }

  @GetMapping("/tenant/{tenantId}/monitors-by-zone/{zone}")
  @ApiOperation(value = "Gets all monitors in a given zone for a specific tenant")
  public PagedContent<MonitorDTO> getMonitorsForZone(@PathVariable String tenantId, @PathVariable String zone, Pageable pageable) {
    return PagedContent.fromPage(
        zoneManagement.getMonitorsForZone(tenantId, zone, pageable)
        .map(MonitorDTO::new));
  }

  @DeleteMapping("/admin/tenant/{tenantId}/zones")
  @ApiOperation(value = "Delete all zones associated with given tenant")
  public void deleteTenantZones(@PathVariable String tenantId, @RequestParam(defaultValue = "true") boolean sendEvents) {
        zoneManagement.removeAllTenantZones(tenantId, sendEvents);
  }
}