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

import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPrivateZone;
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPublicZone;

import com.google.common.collect.Streams;
import com.google.common.math.Stats;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.errors.InvalidTemplateException;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.ZoneAssignmentCount;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.Zone;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;


@Slf4j
@Service
public class MonitorManagement {

  private final BoundMonitorRepository boundMonitorRepository;
  private final ZoneStorage zoneStorage;
  private final MonitorEventProducer monitorEventProducer;
  private final MonitorContentRenderer monitorContentRenderer;
  private final ResourceApi resourceApi;
  private final ZoneManagement zoneManagement;
  private final ZonesProperties zonesProperties;

  private final MonitorRepository monitorRepository;

  @PersistenceContext
  private final EntityManager entityManager;

  private final EnvoyResourceManagement envoyResourceManagement;

  public static final String POLICY_TENANT = "_POLICY_";

  JdbcTemplate jdbcTemplate;

  @Autowired
  public MonitorManagement(MonitorRepository monitorRepository, EntityManager entityManager,
      EnvoyResourceManagement envoyResourceManagement,
      BoundMonitorRepository boundMonitorRepository,
      ZoneStorage zoneStorage,
      MonitorEventProducer monitorEventProducer,
      MonitorContentRenderer monitorContentRenderer,
      ResourceApi resourceApi,
      ZoneManagement zoneManagement, ZonesProperties zonesProperties,
      JdbcTemplate jdbcTemplate) {
    this.monitorRepository = monitorRepository;
    this.entityManager = entityManager;
    this.envoyResourceManagement = envoyResourceManagement;
    this.boundMonitorRepository = boundMonitorRepository;
    this.zoneStorage = zoneStorage;
    this.monitorEventProducer = monitorEventProducer;
    this.monitorContentRenderer = monitorContentRenderer;
    this.resourceApi = resourceApi;
    this.zoneManagement = zoneManagement;
    this.zonesProperties = zonesProperties;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * FOR UNIT TESTING, provides the zone properties
   * @return the {@link ZonesProperties} used by this service
   */
  ZonesProperties getZonesProperties() {
    return zonesProperties;
  }

  /**
   * Gets an individual monitor object by the public facing id.
   *
   * @param tenantId The tenant owning the monitor.
   * @param id       The unique value representing the monitor.
   * @return The monitor object.
   */
  public Optional<Monitor> getMonitor(String tenantId, UUID id) {
    return monitorRepository.findById(id).filter(m -> m.getTenantId().equals(tenantId));
  }

  /**
   * Gets an individual policy monitor by id
   * @param id The unique value representing the monitor.
   * @return The monitor with the provided id.
   * @throws NotFoundException If the monitor does not exist under the POLICY tenant.
   */
  public Optional<Monitor> getPolicyMonitor(UUID id) {
    return getMonitor(POLICY_TENANT, id);
  }

  /**
   * Get a selection of monitor objects across all accounts.
   *
   * @param page The slice of results to be returned.
   * @return The monitors found that match the page criteria.
   */
  public Page<Monitor> getAllMonitors(Pageable page) {
    return monitorRepository.findAll(page);
  }

  /**
   * Same as {@link #getAllMonitors(Pageable page) getAllMonitors} except restricted to a single tenant.
   *
   * @param tenantId The tenant to select monitors from.
   * @param page     The slice of results to be returned.
   * @return The monitors found for the tenant that match the page criteria.
   */
  public Page<Monitor> getMonitors(String tenantId, Pageable page) {
    return monitorRepository.findByTenantId(tenantId, page);
  }

  /**
   * Get all monitors as a stream
   *
   * @return Stream of monitors.
   */
  public Stream<Monitor> getMonitorsAsStream() {
    return Streams.stream(monitorRepository.findAll());
  }

  /**
   * Create a new monitor in the database.
   *
   * @param tenantId   The tenant to create the entity for.
   * @param newMonitor The monitor parameters to store.
   * @return The newly created monitor.
   */
  public Monitor createMonitor(String tenantId, @Valid MonitorCU newMonitor) throws IllegalArgumentException, AlreadyExistsException {
    if (newMonitor.getSelectorScope() == ConfigSelectorScope.LOCAL &&
        newMonitor.getZones() != null && !newMonitor.getZones().isEmpty()) {
      throw new IllegalArgumentException("Local monitors cannot have zones");
    }

    log.debug("Creating monitor={} for tenant={}", newMonitor, tenantId);

    validateMonitoringZones(tenantId, newMonitor.getZones());

    Monitor monitor = new Monitor()
        .setTenantId(tenantId)
        .setMonitorName(newMonitor.getMonitorName())
        .setLabelSelector(newMonitor.getLabelSelector())
        .setContent(newMonitor.getContent())
        .setAgentType(newMonitor.getAgentType())
        .setSelectorScope(newMonitor.getSelectorScope())
        .setZones(newMonitor.getZones());

    monitorRepository.save(monitor);
    final Set<String> affectedEnvoys = bindNewMonitor(monitor);
    sendMonitorBoundEvents(affectedEnvoys);
    return monitor;
  }

  private void validateMonitoringZones(String tenantId, List<String> providedZones) throws IllegalArgumentException {
    if (providedZones == null || providedZones.isEmpty()) {
      return;
    }
    List<String> availableZones = zoneManagement.getAvailableZonesForTenant(tenantId, Pageable.unpaged())
        .stream()
        .map(Zone::getName)
        .collect(Collectors.toList());

    List<String> invalidZones = providedZones.stream()
        .filter(z -> !availableZones.contains(z))
        .collect(Collectors.toList());

    if (!invalidZones.isEmpty()) {
      throw new IllegalArgumentException(String.format("Invalid zone(s) provided: %s",
          String.join(",", invalidZones)));
    }
  }

  /**
   * Performs label selection of the given monitor to locate resources and zones for bindings.
   * @return affected envoy IDs
   */
  Set<String> bindNewMonitor(Monitor monitor) {
    return bindMonitor(monitor, determineMonitoringZones(monitor));
  }

  /**
   * Performs label selection of the given monitor to locate resources for bindings.
   * For remote monitors, this will only perform binding within the given zones.
   * @return affected envoy IDs
   */
  Set<String> bindMonitor(Monitor monitor,
      List<String> zones) {
    final List<ResourceDTO> resources = resourceApi.getResourcesWithLabels(
        monitor.getTenantId(), monitor.getLabelSelector());

    log.debug("Distributing new monitor={} to resources={}", monitor, resources);

    final List<BoundMonitor> boundMonitors = new ArrayList<>();

    if (monitor.getSelectorScope() == ConfigSelectorScope.LOCAL) {
      // AGENT MONITOR

      for (ResourceDTO resource : resources) {

        // agent monitors can only bind to resources that have (or had) an envoy
        if (resource.isAssociatedWithEnvoy()) {

          final ResourceInfo resourceInfo = envoyResourceManagement
              .getOne(monitor.getTenantId(), resource.getResourceId())
              .join();

          try {
            boundMonitors.add(
                bindAgentMonitor(monitor, resource,
                    resourceInfo != null ? resourceInfo.getEnvoyId() : null)
            );
          } catch (InvalidTemplateException e) {
            log.warn("Unable to render monitor={} onto resource={}",
                monitor, resource, e);
          }
        }
      }

    } else {
      // REMOTE MONITOR

      for (ResourceDTO resource : resources) {
        for (String zone : zones) {
          try {
            boundMonitors.add(
                bindRemoteMonitor(monitor, resource, zone)
            );
          } catch (InvalidTemplateException e) {
            log.warn("Unable to render monitor={} onto resource={}",
                monitor, resource, e);
          }
        }
      }

    }

    if (!boundMonitors.isEmpty()) {
      log.debug("Saving boundMonitors={} from monitor={}", boundMonitors, monitor);
      boundMonitorRepository.saveAll(boundMonitors);

    }
    else {
      log.debug("No monitors were bound from monitor={}", monitor);
    }

    return extractEnvoyIds(boundMonitors);
  }

  private void sendMonitorBoundEvent(String envoyId) {
    log.debug("Publishing MonitorBoundEvent for envoy={}", envoyId);
    monitorEventProducer.sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId(envoyId)
    );
  }

  /**
   * Sends monitor bound events to all of the given envoy IDs
   * @param envoyIds envoy IDs to target
   */
  void sendMonitorBoundEvents(Set<String> envoyIds) {
    envoyIds.stream()
        .map(envoyId -> new MonitorBoundEvent().setEnvoyId(envoyId))
        .forEach(monitorEventProducer::sendMonitorEvent);
  }

  private BoundMonitor bindAgentMonitor(Monitor monitor, ResourceDTO resource, String envoyId)
      throws InvalidTemplateException {
    return new BoundMonitor()
        .setMonitor(monitor)
        .setResourceId(resource.getResourceId())
        .setEnvoyId(envoyId)
        .setRenderedContent(monitorContentRenderer.render(monitor.getContent(), resource))
        .setZoneName("");
  }

  private BoundMonitor bindRemoteMonitor(Monitor monitor, ResourceDTO resource, String zone)
      throws InvalidTemplateException {
    final String renderedContent = monitorContentRenderer.render(monitor.getContent(), resource);

    final ResolvedZone resolvedZone = resolveZone(monitor.getTenantId(), zone);

    final Optional<EnvoyResourcePair> result = zoneStorage.findLeastLoadedEnvoy(resolvedZone).join();

    final String envoyId;
    if (result.isPresent()) {
      envoyId = result.get().getEnvoyId();
      zoneStorage.incrementBoundCount(resolvedZone, result.get().getResourceId())
          .join();
    }
    else {
      envoyId = null;
    }

    return new BoundMonitor()
        .setZoneName(zone)
        .setMonitor(monitor)
        .setResourceId(resource.getResourceId())
        .setEnvoyId(envoyId)
        .setRenderedContent(renderedContent);
  }

  private static ResolvedZone getResolvedZoneOfBoundMonitor(BoundMonitor boundMonitor) {
    final String zoneTenantId = boundMonitor.getMonitor().getTenantId();
    final String zoneName = boundMonitor.getZoneName();

    if (zoneTenantId.equals(ResolvedZone.PUBLIC)) {
      return ResolvedZone.createPublicZone(zoneName);
    }
    else {
      return ResolvedZone.createPrivateZone(zoneTenantId, zoneName);
    }
  }

  /**
   * Evaluates unassigned {@link BoundMonitor}s in the given zone and assigns those to
   * least-bound envoys.
   * @param zoneTenantId for private zones, the tenant owning the zone, or <code>null</code> for public zones
   * @param zoneName the zone name
   */
  public void handleNewEnvoyInZone(@Nullable String zoneTenantId, String zoneName) {
    log.debug("Locating bound monitors without assigned envoy with zoneName={} and zoneTenantId={}",
        zoneName, zoneTenantId);

    final ResolvedZone resolvedZone = resolveZone(zoneTenantId, zoneName);

    final List<BoundMonitor> onesWithoutEnvoy;
    if (resolvedZone.isPublicZone()) {
      onesWithoutEnvoy = boundMonitorRepository.findAllWithoutEnvoyInPublicZone(zoneName);
    }
    else {
      onesWithoutEnvoy = boundMonitorRepository.findAllWithoutEnvoyInPrivateZone(zoneTenantId, zoneName);
    }

    log.debug("Found bound monitors without envoy: {}", onesWithoutEnvoy);

    final List<BoundMonitor> assigned = new ArrayList<>(onesWithoutEnvoy.size());

    for (BoundMonitor boundMonitor : onesWithoutEnvoy) {

      final Optional<EnvoyResourcePair> result = zoneStorage.findLeastLoadedEnvoy(resolvedZone).join();
      if (result.isPresent()) {
        boundMonitor.setEnvoyId(result.get().getEnvoyId());
        assigned.add(boundMonitor);
        zoneStorage.incrementBoundCount(resolvedZone, result.get().getResourceId());
      }
    }

    if (!assigned.isEmpty()) {
      log.debug("Assigned existing bound monitors to envoys: {}", assigned);

      boundMonitorRepository.saveAll(assigned);

      sendMonitorBoundEvents(extractEnvoyIds(assigned));
    }
  }

  public void handleEnvoyResourceChangedInZone(@Nullable String tenantId,
      String zoneName, String resourceId,
      String fromEnvoyId, String toEnvoyId) {
    log.debug("Moving bound monitors to new envoy for same resource");

    final ResolvedZone resolvedZone = resolveZone(tenantId, zoneName);

    final List<BoundMonitor> boundToPrev = findBoundMonitorsWithEnvoy(
        resolvedZone, fromEnvoyId, null);

    if (!boundToPrev.isEmpty()) {
      log.debug("Re-assigning bound monitors={} to envoy={}", boundToPrev, toEnvoyId);
      for (BoundMonitor boundMonitor : boundToPrev) {
        boundMonitor.setEnvoyId(toEnvoyId);
      }

      boundMonitorRepository.saveAll(boundToPrev);


      zoneStorage.changeBoundCount(
          createPrivateZone(tenantId, zoneName),
          resourceId,
          boundToPrev.size()
      );

      sendMonitorBoundEvent(toEnvoyId);
    }
    else {
      log.debug("No bound monitors were previously assigned to envoy={}", fromEnvoyId);
    }
  }

  private ResolvedZone resolveZone(String tenantId, String zone) {
    if (zone.startsWith(ResolvedZone.PUBLIC_PREFIX)) {
      return createPublicZone(zone);
    }
    else {
      Assert.notNull(tenantId, "Private zones require a tenantId");
      return createPrivateZone(tenantId, zone);
    }
  }

  private List<String> determineMonitoringZones(Monitor monitor) {
    if (monitor.getSelectorScope() != ConfigSelectorScope.REMOTE) {
      return Collections.emptyList();
    }
    if (monitor.getZones() == null || monitor.getZones().isEmpty()) {
      return zonesProperties.getDefaultZones();
    }
    return monitor.getZones();
  }

  /**
   * Update an existing monitor.
   *
   * @param tenantId      The tenant to create the entity for.
   * @param id            The id of the existing monitor.
   * @param updatedValues The new monitor parameters to store.
   * @return The newly updated monitor.
   */
  public Monitor updateMonitor(String tenantId, UUID id, @Valid MonitorCU updatedValues) {
    Monitor monitor = getMonitor(tenantId, id).orElseThrow(() ->
        new NotFoundException(String.format("No monitor found for %s on tenant %s",
            id, tenantId)));

    if (monitor.getSelectorScope() == ConfigSelectorScope.LOCAL &&
        updatedValues.getZones() != null && !updatedValues.getZones().isEmpty()) {
      throw new IllegalArgumentException("Local monitors cannot have zones");
    }

    validateMonitoringZones(tenantId, updatedValues.getZones());

    final Set<String> affectedEnvoys = new HashSet<>();

    if (updatedValues.getLabelSelector() != null &&
        !updatedValues.getLabelSelector().equals(monitor.getLabelSelector())) {
      // Process potential changes to resource selection and therefore bindings
      // ...only need to process removed and new bindings

      affectedEnvoys.addAll(
          processMonitorLabelSelectorModified(monitor, updatedValues.getLabelSelector())
      );

      monitor.setLabelSelector(updatedValues.getLabelSelector());
    }
    else if (monitor.getLabelSelector() != null) {
      // JPA's EntityManager is a little strange with re-saving (aka merging) an entity
      // that has a field of type Map. It wants to clear the loaded map value, which is
      // disallowed by the org.hibernate.collection.internal.PersistentMap it uses for
      // retrieved maps.
      monitor.setLabelSelector(new HashMap<>(monitor.getLabelSelector()));
    }

    if (updatedValues.getContent() != null &&
        !updatedValues.getContent().equals(monitor.getContent())) {
      // Process potential changes to bound resource rendered content
      // ...only need to process changed bindings

      affectedEnvoys.addAll(
          processMonitorContentModified(monitor, updatedValues.getContent())
      );

      monitor.setContent(updatedValues.getContent());
    }

    if (zonesChanged(updatedValues.getZones(), monitor.getZones())) {
      // Process potential changes to bound zones

      affectedEnvoys.addAll(
          processMonitorZonesModified(monitor, updatedValues.getZones())
      );

      // give JPA a modifiable copy of the given list
      monitor.setZones(new ArrayList<>(updatedValues.getZones()));
    }
    else if (monitor.getZones() != null){
      // See above regarding:
      // JPA's EntityManager is a little strange with re-saving (aka merging) an entity
      monitor.setZones(new ArrayList<>(monitor.getZones()));
    }

    PropertyMapper map = PropertyMapper.get();
    map.from(updatedValues.getMonitorName())
        .whenNonNull()
        .to(monitor::setMonitorName);
    monitorRepository.save(monitor);

    sendMonitorBoundEvents(affectedEnvoys);

    return monitor;
  }

  private static boolean zonesChanged(List<String> updatedZones, List<String> prevZones) {
    return updatedZones != null &&
        ( updatedZones.size() != prevZones.size() ||
            !updatedZones.containsAll(prevZones));
  }

  /**
   * Reconciles the updated zones given against the current state of the given monitor by
   * binding and unbinding as necessary.
   * @return affected envoy IDs
   */
  private Set<String> processMonitorZonesModified(Monitor monitor,
      List<String> updatedZones) {

    // determine new zones
    final List<String> newZones = new ArrayList<>(updatedZones);
    // ...by removing zones on currently stored monitor
    newZones.removeAll(monitor.getZones());

    // determine old zones
    final List<String> oldZones = new ArrayList<>(monitor.getZones());
    // ...by removing the ones still in the update
    oldZones.removeAll(updatedZones);

    // this will also delete the unbound bindings
    final Set<String> affectedEnvoys = unbindByMonitorAndZone(monitor.getId(), oldZones);

    affectedEnvoys.addAll(
        // this will also save the new bindings
        bindMonitor(monitor, newZones)
    );

    return affectedEnvoys;
  }

  /**
   * Reconciles the updated template content against existing bindings of the given monitor.
   * Bindings are updated as needed where the rendered content has changed.
   * @return affected envoy IDs
   */
  private Set<String> processMonitorContentModified(Monitor monitor,
      String updatedContent) {
    final List<BoundMonitor> boundMonitors = boundMonitorRepository
        .findAllByMonitor_Id(monitor.getId());

    final MultiValueMap<String/*resourceId*/, BoundMonitor> groupedByResourceId = new LinkedMultiValueMap<>();
    for (BoundMonitor boundMonitor : boundMonitors) {
      groupedByResourceId.add(boundMonitor.getResourceId(), boundMonitor);
    }

    final List<BoundMonitor> modified = new ArrayList<>();

    for (Entry<String, List<BoundMonitor>> resourceEntry : groupedByResourceId.entrySet()) {

      final String tenantId = monitor.getTenantId();
      final String resourceId = resourceEntry.getKey();
      final ResourceDTO resource = resourceApi
          .getByResourceId(tenantId, resourceId);

      if (resource != null) {
        try {
          final String renderedContent = monitorContentRenderer.render(updatedContent, resource);

          for (BoundMonitor boundMonitor : resourceEntry.getValue()) {
            if (!renderedContent.equals(boundMonitor.getRenderedContent())) {
              boundMonitor.setRenderedContent(renderedContent);
              modified.add(boundMonitor);
            }
          }
        } catch (InvalidTemplateException e) {
          log.warn("Unable to render updatedContent='{}' of monitor={} for resource={}",
              updatedContent, monitor, resource, e);
        }

      }
      else {
        log.warn("Failed to find resourceId={} during processing of monitor={}",
            resourceId, monitor);
      }
    }

    if (!modified.isEmpty()) {
      log.debug("Saving bound monitors with re-rendered content: {}", modified);
      boundMonitorRepository.saveAll(modified);
    }

    return extractEnvoyIds(modified);
  }

  /**
   * Reconciles bindings to the resources selected by the given updated label selector. It
   * creates new bindings and unbinds are necessary.
   * @return affected envoy IDs
   */
  private Set<String> processMonitorLabelSelectorModified(Monitor monitor,
      Map<String, String> updatedLabelSelector) {

    final Set<String> boundResourceIds =
        boundMonitorRepository.findResourceIdsBoundToMonitor(monitor.getId());

    final List<ResourceDTO> selectedResources = resourceApi
        .getResourcesWithLabels(monitor.getTenantId(), updatedLabelSelector);

    final Set<String> selectedResourceIds = selectedResources.stream()
        .map(ResourceDTO::getResourceId)
        .collect(Collectors.toSet());

    final List<String> resourceIdsToUnbind = new ArrayList<>(boundResourceIds);
    resourceIdsToUnbind.removeAll(selectedResourceIds);

    // process un-bindings
    final Set<String> affectedEnvoys =
        unbindByResourceId(monitor.getId(), resourceIdsToUnbind);

    // process new bindings
    selectedResources.stream()
        .filter(resource -> !boundResourceIds.contains(resource.getResourceId()))
        .forEach(resource -> {

          affectedEnvoys.addAll(
              upsertBindingToResource(
                  Collections.singletonList(monitor),
                  resource,
                  null
              )
          );

        });

    return affectedEnvoys;
  }


  /**
   * Delete a monitor.
   *
   * @param tenantId The tenant the monitor belongs to.
   * @param id       The id of the monitor.
   */
  public void removeMonitor(String tenantId, UUID id) {
    Monitor monitor = getMonitor(tenantId, id).orElseThrow(() ->
        new NotFoundException(String.format("No monitor found for %s on tenant %s",
            id, tenantId)));

    // need to unbind before deleting monitor since BoundMonitor references Monitor
    final Set<String> affectedEnvoys = unbindByMonitorId(Collections.singletonList(id));

    sendMonitorBoundEvents(affectedEnvoys);

    monitorRepository.delete(monitor);
  }

  /**
   * Find all monitors associated with a changed resource, and notify the corresponding envoy of the changes.
   * Monitors are found that correspond to both the new labels as well as any old ones so that
   * all the corresponding monitors can be updated for a resource.
   *
   * @param event the new resource event.
   */
  void handleResourceChangeEvent(ResourceEvent event) {
    final String tenantId = event.getTenantId();
    final String resourceId = event.getResourceId();

    if (!event.isLabelsChanged() && event.getReattachedEnvoyId() != null) {
      handleReattachedEnvoy(tenantId, resourceId, event.getReattachedEnvoyId());
      return;
    }

    final List<UUID> boundMonitorIds =
        boundMonitorRepository.findMonitorsBoundToResource(tenantId, resourceId);

    // monitorIdsToUnbind := boundMonitorIds \setminus selectedMonitorIds
    // ...so start with populating with boundMonitorIds
    final Set<UUID> monitorIdsToUnbind = new HashSet<>(boundMonitorIds);

    final List<Monitor> selectedMonitors;
    final ResourceDTO resource = resourceApi.getByResourceId(tenantId, resourceId);
    if (resource != null) {
      // resource created or updated

      if (event.isDeleted()) {
        log.warn("Resource change event indicated deletion, but resource is present: {}", resource);
        // continue with normal processing, assuming it got revived concurrently
      }

      selectedMonitors = getMonitorsFromLabels(resource.getLabels(), tenantId, Pageable.unpaged()).getContent();

      final List<UUID> selectedMonitorIds = selectedMonitors.stream()
          .map(Monitor::getId)
          .collect(Collectors.toList());

      // ...the setminus operation upon monitorIdsToUnbind
      monitorIdsToUnbind.removeAll(selectedMonitorIds);
    }
    else {
      // resource deleted

      selectedMonitors = Collections.emptyList();
      // ...and monitorIdsToUnbind remains ALL of the currently bound
    }

    final Set<String> affectedEnvoys = unbindByMonitorId(monitorIdsToUnbind);

    if (!selectedMonitors.isEmpty()) {
      affectedEnvoys.addAll(
          upsertBindingToResource(selectedMonitors, resource, event.getReattachedEnvoyId())
      );
    }

    sendMonitorBoundEvents(affectedEnvoys);
  }

  /**
   * Finds all the local monitors bound to the resource and re-bind them to the newly attached
   * Envoy
   * @param tenantId
   * @param resourceId
   * @param envoyId
   */
  private void handleReattachedEnvoy(String tenantId, String resourceId, String envoyId) {
    final List<BoundMonitor> bound = boundMonitorRepository
        .findAllLocalByTenantResource(
            tenantId,
            resourceId
        );

    if (!bound.isEmpty()) {
      final Set<String> previousEnvoyIds = extractEnvoyIds(bound);

      bound.forEach(boundMonitor ->
          boundMonitor.setEnvoyId(envoyId)
      );

      boundMonitorRepository.saveAll(bound);

      // now that the re-binding is saved
      // ...tell any previous envoys about loss of binding
      previousEnvoyIds.forEach(this::sendMonitorBoundEvent);
      // ...and tell the attached envoy about the re-bindings
      sendMonitorBoundEvent(envoyId);
    }
  }

  /**
   * Ensures that the given monitors are bound to the given resource or if already bound
   * ensures that the rendered content of the monitor given the resource is up to date.
   * It also ensures existing bindings are updated with the given reattachedEnvoyId, when non-null.
   * @return affected envoy IDs
   */
  Set<String> upsertBindingToResource(List<Monitor> monitors,
      ResourceDTO resource,
      String reattachedEnvoyId) {

    final ResourceInfo resourceInfo = envoyResourceManagement
        .getOne(resource.getTenantId(), resource.getResourceId())
        .join();

    final List<BoundMonitor> boundMonitors = new ArrayList<>();

    final Set<String> affectedEnvoys = new HashSet<>();

    for (Monitor monitor : monitors) {
      final List<BoundMonitor> existing = boundMonitorRepository
          .findAllByMonitor_IdAndResourceId(monitor.getId(), resource.getResourceId());

      if (existing.isEmpty()) {
        // need to create new bindings

        if (monitor.getSelectorScope() == ConfigSelectorScope.LOCAL) {
          // agent/local monitor

          // but skip the resource if it doesn't have (or ever had) an envoy
          if (resource.isAssociatedWithEnvoy()) {
            try {
              boundMonitors.add(
                  bindAgentMonitor(monitor, resource,
                      resourceInfo != null ? resourceInfo.getEnvoyId() : null)
              );
            } catch (InvalidTemplateException e) {
              log.warn("Unable to render monitor={} onto resource={}",
                  monitor, resource, e);
            }
          }
        } else {
          // remote monitor
          try {
            final List<String> zones = determineMonitoringZones(monitor);

            for (String zone : zones) {
              boundMonitors.add(
                  bindRemoteMonitor(monitor, resource, zone)
              );
            }
          } catch (InvalidTemplateException e) {
            log.warn("Unable to render monitor={} onto resource={}",
                monitor, resource, e);
          }
        }
      } else {
        // existing bindings need to be tested and updated for
        // - rendered content changes
        // - envoy re-attachment

        try {
          final String newRenderedContent = monitorContentRenderer
              .render(monitor.getContent(), resource);

          for (BoundMonitor existingBind : existing) {
            boolean updated = false;

            if (!existingBind.getRenderedContent().equals(newRenderedContent)) {
              existingBind.setRenderedContent(newRenderedContent);
              updated = true;
            }

            if (reattachedEnvoyId != null &&
                monitor.getSelectorScope() == ConfigSelectorScope.LOCAL) {
              // need to send an event to old Envoy just in case it's around, but
              // probably won't be due to the re-attachment
              affectedEnvoys.add(existingBind.getEnvoyId());

              existingBind.setEnvoyId(reattachedEnvoyId);
              updated = true;
            }

            if (updated) {
              boundMonitors.add(existingBind);
            }
          }

        } catch (InvalidTemplateException e) {
          log.warn("Unable to render monitor={} onto resource={}, removing existing bindings={}",
              monitor, resource, existing, e);

          boundMonitorRepository.deleteAll(existing);
          affectedEnvoys.addAll(
              extractEnvoyIds(existing)
          );
        }
      }
    }

    if (!boundMonitors.isEmpty()) {
      log.debug("Saving boundMonitors={} due to binding of monitors={} to resource={}",
          boundMonitors, monitors, resource);
      boundMonitorRepository.saveAll(boundMonitors);
    } else {
      log.debug("None of monitors={} needed to be bound to resource={}",
          monitors, resource);
    }

    affectedEnvoys.addAll(
        extractEnvoyIds(boundMonitors)
    );

    return affectedEnvoys;
  }

  /**
   * Removes all bindings associated with the given monitor IDs.
   * @return affected envoy IDs
   */
  Set<String> unbindByMonitorId(Collection<UUID> monitorIdsToUnbind) {
    if (monitorIdsToUnbind.isEmpty()) {
      return new HashSet<>();
    }

    final List<BoundMonitor> boundMonitors =
        boundMonitorRepository.findAllByMonitor_IdIn(monitorIdsToUnbind);

    log.debug("Unbinding {} from monitorIds={}",
        boundMonitors, monitorIdsToUnbind);
    boundMonitorRepository.deleteAll(boundMonitors);
    decrementBoundCounts(boundMonitors);

    return extractEnvoyIds(boundMonitors);
  }

  /**
   * Removes all bindings associated with the given monitor and resources.
   * @return affected envoy IDs
   */
  private Set<String> unbindByResourceId(UUID monitorId,
      List<String> resourceIdsToUnbind) {
    if (resourceIdsToUnbind.isEmpty()) {
      return new HashSet<>();
    }

    final List<BoundMonitor> boundMonitors = boundMonitorRepository
        .findAllByMonitor_IdAndResourceIdIn(monitorId, resourceIdsToUnbind);

    log.debug("Unbinding {} from monitorId={} resourceIds={}", boundMonitors,
        monitorId, resourceIdsToUnbind);
    boundMonitorRepository.deleteAll(boundMonitors);
    decrementBoundCounts(boundMonitors);

    return extractEnvoyIds(boundMonitors);
  }

  /**
   * Removes all bindings associated with the given monitor and zones.
   * @return affected envoy IDs
   */
  private Set<String> unbindByMonitorAndZone(UUID monitorId, List<String> zones) {

    final List<BoundMonitor> needToDelete = boundMonitorRepository
        .findAllByMonitor_IdAndZoneNameIn(monitorId, zones);

    log.debug("Unbinding monitorId={} from zones={}: {}", monitorId, zones, needToDelete);
    boundMonitorRepository.deleteAll(needToDelete);

    decrementBoundCounts(needToDelete);

    return extractEnvoyIds(needToDelete);
  }

  /**
   * Extracts the distinct, non-null envoy IDs from the given bindings.
   */
  static Set<String> extractEnvoyIds(List<BoundMonitor> boundMonitors) {
    return boundMonitors.stream()
        .map(BoundMonitor::getEnvoyId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * Determines the resource id each monitor is bound to and decreases the count of
   * monitors it is bound to.
   * @param needToDelete A list of bound monitors to act on.
   */
  private void decrementBoundCounts(List<BoundMonitor> needToDelete) {
    Map<String, String> envoyToResource = new HashMap<>();
    for (BoundMonitor boundMonitor : needToDelete) {
      if (boundMonitor.getEnvoyId() != null &&
          boundMonitor.getMonitor().getSelectorScope() == ConfigSelectorScope.REMOTE) {
        ResolvedZone zone = getResolvedZoneOfBoundMonitor(boundMonitor);
        String envoyId = boundMonitor.getEnvoyId();
        String resourceId = envoyToResource.get(envoyId);
        // If we don't know the resourceId yet, try look it up
        if (resourceId == null) {
          envoyToResource.putAll(zoneStorage.getEnvoyIdToResourceIdMap(zone).join());
          resourceId = envoyToResource.get(envoyId);
        }

        // If the resourceId is still not known, the envoy must have disconnected, so skip
        if (resourceId != null) {
          zoneStorage.decrementBoundCount(zone, resourceId);
        }
      }
    }
  }

  /**
   * takes in a Mapping of labels for a tenant, builds and runs the query to match to those labels
   * @param labels the labels that we need to match to
   * @param tenantId The tenant associated to the resource
   * @return the list of Monitor's that match the labels
   */
  @SuppressWarnings("Duplicates")
  public Page<Monitor> getMonitorsFromLabels(Map<String, String> labels, String tenantId, Pageable page) throws IllegalArgumentException {
    if(labels.size() == 0) {
      throw new IllegalArgumentException("Labels must be provided for search");
    }

    MapSqlParameterSource paramSource = new MapSqlParameterSource();
    paramSource.addValue("tenantId", tenantId);
    StringBuilder builder = new StringBuilder("SELECT monitors.id FROM monitors JOIN monitor_label_selectors AS ml WHERE monitors.id = ml.monitor_id AND monitors.id IN ");
    builder.append("(SELECT monitor_id from monitor_label_selectors WHERE monitors.id IN (SELECT id FROM monitors WHERE tenant_id = :tenantId) AND ");
    builder.append("monitors.id IN (SELECT search_labels.monitor_id FROM (SELECT monitor_id, COUNT(*) AS count FROM monitor_label_selectors GROUP BY monitor_id) AS total_labels JOIN (SELECT monitor_id, COUNT(*) AS count FROM monitor_label_selectors WHERE ");
    int i = 0;
    labels.size();
    for(Map.Entry<String, String> entry : labels.entrySet()) {
      if(i > 0) {
        builder.append(" OR ");
      }
      builder.append("(label_selector = :label"+ i +" AND label_selector_key = :labelKey" + i + ")");
      paramSource.addValue("label"+i, entry.getValue());
      paramSource.addValue("labelKey"+i, entry.getKey());
      i++;
    }
    builder.append(" GROUP BY monitor_id) AS search_labels WHERE total_labels.monitor_id = search_labels.monitor_id AND search_labels.count >= total_labels.count GROUP BY search_labels.monitor_id)");

    builder.append(") ORDER BY monitors.id");
    paramSource.addValue("i", i);

    NamedParameterJdbcTemplate namedParameterTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
    final List<UUID> monitorIds = namedParameterTemplate.query(builder.toString(), paramSource,
        (resultSet, rowIndex) -> UUID.fromString(resultSet.getString(1))
    );

    final List<Monitor> monitors = new ArrayList<>();
    // use JPA to retrieve and resolve the Monitor objects and then convert Iterable result to list
    for (Monitor monitor : monitorRepository.findAllById(monitorIds)) {
      monitors.add(monitor);
    }
    if (page.isPaged()) {
      int start = page.getPageSize() * page.getPageNumber();
      int end = start + page.getPageSize();
      if (end > monitors.size()) {
        end = monitors.size();
      }
      List<Monitor> results;
      try {
        results = monitors.subList(start, end);
      } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
        results = Collections.emptyList();
      }
      return new PageImpl<>(results, page, monitors.size());
    } else {
      return new PageImpl<>(monitors, page, monitors.size());
    }
  }

  /**
   * Unassigns the old envoy from all relevant bound monitors,
   * then attempts to reassign them to a different envoy if one is available.
   * @param zoneTenantId The tenantId of the resolved zone
   *  or <code>null</code> if it is a public zone.
   * @param zoneName The name of the resolved zone.
   * @param envoyId The envoy id that has disconnected.
   */
  public void handleExpiredEnvoy(@Nullable String zoneTenantId, String zoneName, String envoyId) {
    log.debug("Reassigning bound monitors for disconnected envoy={} with zoneName={} and zoneTenantId={}",
        envoyId, zoneName, zoneTenantId);
    List<BoundMonitor> boundMonitors = getAllBoundMonitorsByEnvoyId(envoyId, Pageable.unpaged()).getContent();
    if (boundMonitors.isEmpty()) {
      return;
    }
    for (BoundMonitor boundMonitor : boundMonitors) {
      boundMonitor.setEnvoyId(null);
    }

    boundMonitorRepository.saveAll(boundMonitors);
    handleNewEnvoyInZone(zoneTenantId, zoneName);
  }

  public CompletableFuture<List<ZoneAssignmentCount>> getZoneAssignmentCounts(
      @Nullable String zoneTenantId, String zoneName) {

    final ResolvedZone zone = resolveZone(zoneTenantId, zoneName);

    return zoneStorage.getZoneBindingCounts(zone)
        .thenApply(bindingCounts ->
            bindingCounts.entrySet().stream()
                .map(entry ->
                    new ZoneAssignmentCount()
                        .setResourceId(entry.getKey().getResourceId())
                        .setEnvoyId(entry.getKey().getEnvoyId())
                        .setAssignments(entry.getValue()))
                .collect(Collectors.toList()));
  }

  /**
   * Rebalances a zone by re-assigning bound monitors from envoys that are over-assigned
   * to the least loaded envoys.
   * @param zoneTenantId for private zones, the tenant owning the zone, or null for public zones
   * @param zoneName the zone name
   * @return the number of bound monitors that were re-assigned
   */
  public CompletableFuture<Integer> rebalanceZone(@Nullable String zoneTenantId, String zoneName) {
    final ResolvedZone zone = resolveZone(zoneTenantId, zoneName);

    return zoneStorage.getZoneBindingCounts(zone)
        .thenApply(bindingCounts ->
            rebalanceWithZoneBindingCounts(zone, bindingCounts)
        );
  }

  public MultiValueMap<String, String> getTenantMonitorLabelSelectors(String tenantId) {
    final List<Map.Entry> distinctLabelTuples = entityManager.createNamedQuery(
        "Monitor.getDistinctLabelSelectors", Map.Entry.class)
        .setParameter("tenantId", tenantId)
        .getResultList();

    final MultiValueMap<String,String> combined = new LinkedMultiValueMap<>();
    for (Entry entry : distinctLabelTuples) {
      combined.add((String)entry.getKey(), (String)entry.getValue());
    }
    return combined;
  }

  private int rebalanceWithZoneBindingCounts(ResolvedZone zone,
      Map<EnvoyResourcePair, Integer> bindingCounts) {
    if (bindingCounts.size() <= 1) {
      // nothing to rebalance if only one or none envoys in zone
      return 0;
    }

    log.debug("Rebalancing zone={} given bindingCounts={}", zone, bindingCounts);

    final List<Integer> values = bindingCounts.values().stream()
        .filter(value ->
            zonesProperties.isRebalanceEvaluateZeroes() || value != 0)
        .collect(Collectors.toList());

    @SuppressWarnings("UnstableApiUsage")
    final Stats stats = Stats.of(values);

    final double stddev = stats.populationStandardDeviation();
    final double avg = stats.mean();
    final double threshold = avg + stddev * zonesProperties.getRebalanceStandardDeviations();
    // round up to be lean towards slightly less reassignments
    final int avgInt = (int) Math.ceil(avg);

    final List<BoundMonitor> overAssigned = new ArrayList<>();

    for (Entry<EnvoyResourcePair, Integer> entry : bindingCounts.entrySet()) {
      if (entry.getValue() > threshold) {

        // pick off enough bound monitors to bring this one down to average
        final int amountToUnassign = entry.getValue() - avgInt;
        final PageRequest limit = PageRequest.of(0, amountToUnassign);

        final List<BoundMonitor> boundMonitors = findBoundMonitorsWithEnvoy(
            zone, entry.getKey().getEnvoyId(), limit);

        overAssigned.addAll(boundMonitors);

        // decrease the assignment count of bound monitors
        zoneStorage.changeBoundCount(
            zone, entry.getKey().getResourceId(), -amountToUnassign
        );
      }
    }

    log.debug("Rebalancing boundMonitors={} in zone={}", overAssigned, zone);

    final Set<String> overassignedEnvoyIds = extractEnvoyIds(overAssigned);

    // "unassign" the bound monitors
    overAssigned.forEach(boundMonitor -> boundMonitor.setEnvoyId(null));
    boundMonitorRepository.saveAll(overAssigned);

    // tell previous envoys to stop unassigned monitors
    sendMonitorBoundEvents(overassignedEnvoyIds);
    // ...and then this will "re-assign" the bound monitors and send out new bound events
    handleNewEnvoyInZone(zone.getTenantId(), zone.getName());

    return overAssigned.size();
  }

  private List<BoundMonitor> findBoundMonitorsWithEnvoy(ResolvedZone zone, String envoyId,
      PageRequest limit) {
    final List<BoundMonitor> boundMonitors;
    if (zone.isPublicZone()) {
      boundMonitors = boundMonitorRepository.findWithEnvoyInPublicZone(
          zone.getName(), envoyId, limit
      );
    } else {
      boundMonitors = boundMonitorRepository.findWithEnvoyInPrivateZone(
          zone.getTenantId(), zone.getName(), envoyId, limit
      );
    }
    return boundMonitors;
  }

  public Page<BoundMonitor> getAllBoundMonitorsByEnvoyId(String envoyId, Pageable page) {
    return boundMonitorRepository.findAllByEnvoyId(envoyId, page);
  }

  public Page<BoundMonitor> getAllBoundMonitorsByTenantId(String tenantId, Pageable page) {
    return boundMonitorRepository.findAllByMonitor_TenantId(tenantId, page);
  }
}