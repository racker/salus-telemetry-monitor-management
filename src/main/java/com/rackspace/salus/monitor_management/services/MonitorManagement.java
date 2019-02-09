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

import com.rackspace.salus.telemetry.model.*;
import com.rackspace.salus.telemetry.messaging.*;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.validation.Valid;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@Service
public class MonitorManagement {
    private final MonitorRepository monitorRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    @Autowired
    public MonitorManagement(MonitorRepository monitorRepository, EntityManager entityManager) {
        this.monitorRepository = monitorRepository;
        this.entityManager = entityManager;
    }

    // /**
    //  * Creates or updates the monitor depending on whether the ID already exists.
    //  * Also sends a monitor event to kafka for consumption by other services.
    //  *
    //  * @param monitor The monitor object to create/update in the database.
    //  * @param oldLabels The labels of the monitor prior to any modifications.
    //  * @param presenceMonitoringStateChanged Whether the presence monitoring flag has been switched.
    //  * @param operation The type of event that occurred. e.g. create, update, or delete.
    //  * @return
    //  */
//    public Monitor saveAndPublishMonitor(Monitor monitor, Map<String, String> oldLabels,
//                                           boolean presenceMonitoringStateChanged, OperationType operation) {
    public Monitor saveAndPublishMonitor(Monitor monitor) {
        monitorRepository.save(monitor);
        return monitor;
    }

    // /**
    //  * Gets an individual monitor object by the public facing id.
    //  * @param tenantId The tenant owning the monitor.
    //  * @param monitorId The unique value representing the monitor.
    //  * @return The monitor object.
    //  */
    // public Monitor getMonitor(String tenantId, String monitorId) {
    //     CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    //     CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
    //     Root<Monitor> root = cr.from(Monitor.class);
    //     cr.select(root).where(cb.and(
    //             cb.equal(root.get(Monitor_.tenantId), tenantId),
    //             cb.equal(root.get(Monitor_.monitorId), monitorId)));

    //     Monitor result;
    //     try {
    //         result = entityManager.createQuery(cr).getSingleResult();
    //     } catch (NoResultException e) {
    //         result = null;
    //     }

    //     return result;
    // }

    // /**
    //  * Get a selection of monitor objects across all accounts.
    //  * @param page The slice of results to be returned.
    //  * @return The monitors found that match the page criteria.
    //  */
    public Page<Monitor> getAllMonitors(Pageable page) {
        return monitorRepository.findAll(page);
    }

    // /**
    //  * Same as {@link #getAllMonitors(Pageable page) getAllMonitors} except restricted to a single tenant.
    //  * @param tenantId The tenant to select monitors from.
    //  * @param page The slice of results to be returned.
    //  * @return The monitors found for the tenant that match the page criteria.
    //  */
    // public Page<Monitor> getMonitors(String tenantId, Pageable page) {
    //     CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    //     CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
    //     Root<Monitor> root = cr.from(Monitor.class);
    //     cr.select(root).where(
    //             cb.equal(root.get(Monitor_.tenantId), tenantId));

    //     List<Monitor> monitors = entityManager.createQuery(cr).getResultList();

    //     return new PageImpl<>(monitors, page, monitors.size());
    // }

    // /**
    // public List<Monitor> getMonitors(String tenantId, Map<String, String> labels) {
    //     // use geoff's label search query
    // }*/

    // /**
    //  * Get all monitors where the presence monitoring field matches the parameter provided.
    //  * @param presenceMonitoringEnabled Whether presence monitoring is enabled or not.
    //  * @return Stream of monitors.
    //  */
    // public Stream<Monitor> getMonitors(boolean presenceMonitoringEnabled) {
    //     CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    //     CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
    //     Root<Monitor> root = cr.from(Monitor.class);

    //     cr.select(root).where(
    //             cb.equal(root.get(Monitor_.presenceMonitoringEnabled), presenceMonitoringEnabled));

    //     return entityManager.createQuery(cr).getResultStream();
    // }

    // /**
    //  * Similar to {@link #getMonitors(boolean presenceMonitoringEnabled) getMonitors} except restricted to a
    //  * single tenant, and returns a list.
    //  * @param tenantId The tenant to select monitors from.
    //  * @param presenceMonitoringEnabled Whether presence monitoring is enabled or not.
    //  * @param page The slice of results to be returned.
    //  * @return A page or monitors matching the given criteria.
    //  */
    // public Page<Monitor> getMonitors(String tenantId, boolean presenceMonitoringEnabled, Pageable page) {
    //     CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    //     CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
    //     Root<Monitor> root = cr.from(Monitor.class);

    //     cr.select(root).where(cb.and(
    //             cb.equal(root.get(Monitor_.tenantId), tenantId),
    //             cb.equal(root.get(Monitor_.presenceMonitoringEnabled), presenceMonitoringEnabled)));

    //     List<Monitor> monitors = entityManager.createQuery(cr).getResultList();

    //     return new PageImpl<>(monitors, page, monitors.size());
    // }

    // /**
    //  * Create a new monitor in the database and publish an event to kafka.
    //  * @param tenantId The tenant to create the entity for.
    //  * @param newMonitor The monitor parameters to store.
    //  * @return The newly created monitor.
    //  * @throws IllegalArgumentException
    //  * @throws MonitorAlreadyExists
    //  */
    // public Monitor createMonitor(String tenantId, @Valid MonitorCreate newMonitor) throws IllegalArgumentException, MonitorAlreadyExists {
    //     Monitor existing = getMonitor(tenantId, newMonitor.getMonitorId());
    //     if (existing != null) {
    //         throw new MonitorAlreadyExists(String.format("Monitor already exists with identifier %s on tenant %s",
    //                 newMonitor.getMonitorId(), tenantId));
    //     }

    //     Monitor monitor = new Monitor()
    //             .setTenantId(tenantId)
    //             .setMonitorId(newMonitor.getMonitorId())
    //             .setLabels(newMonitor.getLabels())
    //             .setPresenceMonitoringEnabled(newMonitor.getPresenceMonitoringEnabled());

    //     monitor = saveAndPublishMonitor(monitor, null, monitor.getPresenceMonitoringEnabled(), OperationType.CREATE);

    //     return monitor;
    // }

    // /**
    //  * Update an existing monitor and publish an event to kafka.
    //  * @param tenantId The tenant to create the entity for.
    //  * @param monitorId The id of the existing monitor.
    //  * @param updatedValues The new monitor parameters to store.
    //  * @return The newly updated monitor.
    //  */
    // public Monitor updateMonitor(String tenantId, String monitorId, @Valid MonitorUpdate updatedValues) {
    //     Monitor monitor = getMonitor(tenantId, monitorId);
    //     if (monitor == null) {
    //         throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
    //                 monitorId, tenantId));
    //     }
    //     Map<String, String> oldLabels = new HashMap<>(monitor.getLabels());
    //     boolean presenceMonitoringStateChange = false;
    //     if (updatedValues.getPresenceMonitoringEnabled() != null) {
    //         presenceMonitoringStateChange = monitor.getPresenceMonitoringEnabled().booleanValue()
    //                 != updatedValues.getPresenceMonitoringEnabled().booleanValue();
    //     }

    //     PropertyMapper map = PropertyMapper.get();
    //     map.from(updatedValues.getLabels())
    //             .whenNonNull()
    //             .to(monitor::setLabels);
    //     map.from(updatedValues.getPresenceMonitoringEnabled())
    //             .whenNonNull()
    //             .to(monitor::setPresenceMonitoringEnabled);

    //     saveAndPublishMonitor(monitor, oldLabels, presenceMonitoringStateChange, OperationType.UPDATE);

    //     return monitor;
    // }

    // /**
    //  * Delete a monitor and publish an event to kafka.
    //  * @param tenantId The tenant the monitor belongs to.
    //  * @param monitorId The id of the monitor.
    //  */
    // public void removeMonitor(String tenantId, String monitorId) {
    //     Monitor monitor = getMonitor(tenantId, monitorId);
    //     if (monitor != null) {
    //         monitorRepository.deleteById(monitor.getId());
    //         publishMonitorEvent(monitor, null, monitor.getPresenceMonitoringEnabled(), OperationType.DELETE);
    //     } else {
    //         throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
    //                 monitorId, tenantId));
    //     }
    // }

    // /**
    //  * Registers or updates monitors in the datastore.
    //  * Prefixes the labels received from the envoy so they do not clash with any api specified values.
    //  *
    //  * @param attachEvent The event triggered from the Ambassador by any envoy attachment.
    //  */
    // public void handleEnvoyAttach(AttachEvent attachEvent) {
    //     String tenantId = attachEvent.getTenantId();
    //     String monitorId = attachEvent.getMonitorId();
    //     Map<String, String> labels = attachEvent.getLabels();
    //     labels = applyNamespaceToKeys(labels, ENVOY_NAMESPACE);

    //     Monitor existing = getMonitor(tenantId, monitorId);

    //     if (existing == null) {
    //         log.debug("No monitor found for new envoy attach");
    //         Monitor newMonitor = new Monitor()
    //                 .setTenantId(tenantId)
    //                 .setMonitorId(monitorId)
    //                 .setLabels(labels)
    //                 .setPresenceMonitoringEnabled(true);
    //         saveAndPublishMonitor(newMonitor, null, true, OperationType.CREATE);
    //     } else {
    //         log.debug("Found existing monitor related to envoy: {}", existing.toString());

    //         Map<String,String> oldLabels = new HashMap<>(existing.getLabels());

    //         updateEnvoyLabels(existing, labels);
    //         saveAndPublishMonitor(existing, oldLabels, false, OperationType.UPDATE);
    //     }
    // }

    // /**
    //  * When provided with a list of envoy labels determine which ones need to be modified and perform an update.
    //  * @param monitor The monitor to update.
    //  * @param envoyLabels The list of labels received from a newly connected envoy.
    //  */
    // private void updateEnvoyLabels(Monitor monitor, Map<String, String> envoyLabels) {
    //     AtomicBoolean updated = new AtomicBoolean(false);
    //     Map<String, String> monitorLabels = monitor.getLabels();
    //     Map<String, String> oldLabels = new HashMap<>(monitorLabels);

    //     oldLabels.entrySet().stream()
    //         .filter(entry -> entry.getKey().startsWith(ENVOY_NAMESPACE))
    //         .forEach(entry -> {
    //             if (envoyLabels.containsKey(entry.getKey())) {
    //                 if (envoyLabels.get(entry.getKey()) != entry.getValue()) {
    //                     updated.set(true);
    //                     monitorLabels.put(entry.getKey(), entry.getValue());
    //                 }
    //             } else {
    //                 updated.set(true);
    //                 monitorLabels.remove(entry.getKey());
    //             }
    //         });
    //     if (updated.get()) {
    //         saveAndPublishMonitor(monitor, oldLabels, false, OperationType.UPDATE);
    //     }
    // }

    // /**
    //  * Publish a monitor event to kafka for consumption by other services.
    //  * @param monitor The updated monitor the operation was performed on.
    //  * @param oldLabels The monitor labels prior to any update.
    //  * @param presenceMonitoringStateChanged Whether the presence monitoring flag has been switched.
    //  * @param operation The type of event that occurred. e.g. create, update, or delete.
    //  */
    // private void publishMonitorEvent(Monitor monitor, Map<String, String> oldLabels, boolean presenceMonitoringStateChanged, OperationType operation) {
    //     MonitorEvent event = new MonitorEvent();
    //     event.setMonitor(monitor);
    //     event.setOldLabels(oldLabels);
    //     event.setPresenceMonitorChange(presenceMonitoringStateChanged);
    //     event.setOperation(operation);

    //     kafkaEgress.sendMonitorEvent(event);
    // }

    // /**
    //  * Receives a map of strings and adds the given namespace as a prefix to the key.
    //  * @param map The map to modify.
    //  * @param namespace Prefix to apply to map's keys.
    //  * @return Original map but with the namespace prefix applied to all keys.
    //  */
    // private Map<String, String> applyNamespaceToKeys(Map<String, String> map, String namespace) {
    //     Map<String, String> prefixedMap = new HashMap<>();
    //     map.forEach((name, value) -> {
    //         prefixedMap.put(namespace + name, value);
    //     });
    //     return prefixedMap;
    // }

    // /**
    //  * This can be used to force a presence monitoring change if it is currently running but should not be.
    //  *
    //  * If the monitor no longer exists, we will still send an event in case presence monitoring is still active.
    //  * This case will also ensure the monitor mgmt service removed any active monitors.
    //  *
    //  * @param tenantId The tenant associated to the monitor.
    //  * @param monitorId THe id of the monitor we need to disable monitoring of.
    //  */
    // private void removePresenceMonitoring(String tenantId, String monitorId) {
    //     Monitor monitor = getMonitor(tenantId, monitorId);
    //     if (monitor == null) {
    //         log.debug("No monitor found to remove presence monitoring");
    //         monitor = new Monitor()
    //                 .setTenantId(tenantId)
    //                 .setMonitorId(monitorId)
    //                 .setPresenceMonitoringEnabled(false);
    //     } else {
    //         monitor.setPresenceMonitoringEnabled(false);
    //     }

    //     saveAndPublishMonitor(monitor, monitor.getLabels(), true, OperationType.UPDATE);
    // }

    // //public Monitor migrateMonitorToTenant(String oldTenantId, String newTenantId, String identifierName, String identifierValue) {}
}
