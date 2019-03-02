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

import com.coreos.jetcd.op.Op;
import com.rackspace.salus.monitor_management.config.MonitorManagementProperties;
import com.rackspace.salus.monitor_management.web.model.MonitorCreate;
import com.rackspace.salus.monitor_management.web.model.MonitorUpdate;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.messaging.MonitorEvent;
import com.rackspace.salus.telemetry.messaging.OperationType;
import com.rackspace.salus.telemetry.model.*;
import com.rackspace.salus.telemetry.model.Monitor;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import jdk.management.resource.ResourceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Root;
import javax.validation.Valid;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Stream;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import org.springframework.web.client.RestTemplate;
import java.net.URI;


@Slf4j
@Service
public class MonitorManagement {
    private final MonitorEventProducer monitorEventProducer;

    private final MonitorRepository monitorRepository;

    private final RestTemplate restTemplate;

    private final MonitorManagementProperties monitorManagementProperties;

    @PersistenceContext
    private final EntityManager entityManager;

    private final EnvoyResourceManagement envoyResourceManagement;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    public MonitorManagement(MonitorRepository monitorRepository, EntityManager entityManager,
                             EnvoyResourceManagement envoyResourceManagement,
                             MonitorEventProducer monitorEventProducer,
                             RestTemplateBuilder restTemplateBuilder,
                             MonitorManagementProperties monitorManagementProperties) {
        this.monitorRepository = monitorRepository;
        this.entityManager = entityManager;
        this.envoyResourceManagement = envoyResourceManagement;
        this.monitorEventProducer = monitorEventProducer;
        this.restTemplate = restTemplateBuilder.build();
        this.monitorManagementProperties = monitorManagementProperties;
    }

    /**
     * Gets an individual monitor object by the public facing id.
     *
     * @param tenantId  The tenant owning the monitor.
     * @param id The unique value representing the monitor.
     * @return The monitor object.
     */
    public Monitor getMonitor(String tenantId, UUID id) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
        Root<Monitor> root = cr.from(Monitor.class);
        cr.select(root).where(cb.and(
                cb.equal(root.get(Monitor_.tenantId), tenantId),
                cb.equal(root.get(Monitor_.id), id)));

        Monitor result;
        try {
            result = entityManager.createQuery(cr).getSingleResult();
        } catch (NoResultException e) {
            result = null;
        }
        return result;
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
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
        Root<Monitor> root = cr.from(Monitor.class);
        cr.select(root).where(
                cb.equal(root.get(Monitor_.tenantId), tenantId));

        List<Monitor> monitors = entityManager.createQuery(cr).getResultList();

        return new PageImpl<>(monitors, page, monitors.size());
    }

    /**
     * Get all monitors as a stream
     *
     * @return Stream of monitors.
     */
    public Stream<Monitor> getMonitorsAsStream() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
        Root<Monitor> root = cr.from(Monitor.class);
        cr.select(root);
        return entityManager.createQuery(cr).getResultStream();
    }


    /**
     * Create a new monitor in the database.
     *
     * @param tenantId   The tenant to create the entity for.
     * @param newMonitor The monitor parameters to store.
     * @return The newly created monitor.
     */
    public Monitor createMonitor(String tenantId, @Valid MonitorCreate newMonitor) throws IllegalArgumentException, AlreadyExistsException {
        Monitor monitor = new Monitor()
                .setTenantId(tenantId)
                .setMonitorName(newMonitor.getMonitorName())
                .setLabels(newMonitor.getLabels())
                .setContent(newMonitor.getContent())
                .setAgentType(AgentType.valueOf(newMonitor.getAgentType()))
                .setSelectorScope(ConfigSelectorScope.valueOf(newMonitor.getSelectorScope()))
                .setTargetTenant(newMonitor.getTargetTenant());
                

        monitorRepository.save(monitor);
        publishMonitor(monitor, OperationType.CREATE, null);
        return monitor;
    }

    boolean publishMonitor(Monitor monitor, OperationType operationType, Map<String, String> oldLabels) {
        String endpoint = "/api/tenant/tenantId/resources/labels".replace("tenantId", monitor.getTenantId());
        try {
            List<Resource> oldResources = null;
            URI uri = new URI(monitorManagementProperties.getResourceManagerUrl() + endpoint);


            RequestEntity<Map<String, String>> requestEntity = RequestEntity.post(uri).body(monitor.getLabels());
            ResponseEntity<List<Resource>> resp = restTemplate.exchange(requestEntity,
                    new ParameterizedTypeReference<List<Resource>>() {
                    });
            if (resp.getStatusCode() != HttpStatus.OK) {
                log.error("get failed on: " + uri, resp.getStatusCode());
                return false;
            }
            List<Resource> resources = resp.getBody();
            if (oldLabels != null && !oldLabels.equals(monitor.getLabels())) {
                requestEntity = RequestEntity.post(uri).body(oldLabels);
                ResponseEntity<List<Resource>> oldResp = restTemplate.exchange(requestEntity,
                        new ParameterizedTypeReference<List<Resource>>() {
                        });
                if (oldResp.getStatusCode() != HttpStatus.OK) {
                    log.error("get failed on: " + uri, oldResp.getStatusCode());
                    return false;
                }
                oldResources = resp.getBody();

            }
            resources.addAll(oldResources);
            Map<String, Resource> resourceMap = new HashMap<>();
            // Eliminate duplicate resources
            for (Resource r: resources) {
                resourceMap.put(r.getResourceId(), r);
            }
            for (String id : resourceMap.keySet()) {
                Resource r = resourceMap.get(id);
                String[] identifiers = r.getResourceId().split(":");

                ResourceInfo resourceInfo = envoyResourceManagement
                        .getOne(monitor.getTenantId(), identifiers[0], identifiers[1]).join().get(0);

                // Make sure to send the event to Kafka
                MonitorEvent monitorEvent = new MonitorEvent()
                        .setFromMonitor(monitor)
                        .setOperationType(operationType)
                        .setEnvoyId(resourceInfo.getEnvoyId());

                monitorEventProducer.sendMonitorEvent(monitorEvent);

            }
        } catch (URISyntaxException e) {
            log.error("URI syntax exception on: " + endpoint);
            return false;
        }
        return true;
    }
    /**
     * Update an existing monitor.
     *
     * @param tenantId      The tenant to create the entity for.
     * @param id            The id of the existing monitor.
     * @param updatedValues The new monitor parameters to store.
     * @return The newly updated monitor.
     */
    public Monitor updateMonitor(String tenantId, UUID id, @Valid MonitorUpdate updatedValues) {
        Monitor monitor = getMonitor(tenantId, id);
        if (monitor == null) {
            throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
                    id, tenantId));
        }
        Map<String, String> oldLabels = monitor.getLabels();
        PropertyMapper map = PropertyMapper.get();
        map.from(updatedValues.getLabels())
                .whenNonNull()
                .to(monitor::setLabels);
        map.from(updatedValues.getContent())
                .whenNonNull()
                .to(monitor::setContent);
        map.from(updatedValues.getMonitorName())
                .whenNonNull()
                .to(monitor::setMonitorName);
        monitor.setTargetTenant(updatedValues.getTargetTenant());
        monitorRepository.save(monitor);
        publishMonitor(monitor, OperationType.UPDATE, oldLabels);
        return monitor;
    }


    /**
     * Delete a monitor.
     *
     * @param tenantId  The tenant the monitor belongs to.
     * @param id The id of the monitor.
     */
    public void removeMonitor(String tenantId, UUID id) {
        Monitor monitor = getMonitor(tenantId, id);
        if (monitor != null) {
            monitorRepository.deleteById(monitor.getId());
            publishMonitor(monitor, OperationType.DELETE, null);
        } else {
            throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
                    id, tenantId));
        }
    }

    public List<Monitor> getMonitorsWithLabels(String tenantId, Map<String, String> labels) {
        // Adam's code
        return null;
    }

    public void handleResourceEvent(ResourceEvent event) {
        Resource r = event.getResource();
        List<Monitor> oldMonitors = null;
        List<Monitor> monitors = getMonitorsWithLabels(event.getResource().getTenantId(), event.getResource().getLabels());
        if (event.getOldLabels() != null && !event.getOldLabels().equals(event.getResource().getLabels())) {
            oldMonitors = getMonitorsWithLabels(event.getResource().getTenantId(), event.getOldLabels());
        }
        if (oldMonitors != null) {
            monitors.addAll(oldMonitors);
        }
        Map<UUID, Monitor> monitorMap = new HashMap<>();
        // Eliminate duplicate monitors
        for (Monitor m: monitors) {
            monitorMap.put(m.getId(), m);
        }
        for (UUID id : monitorMap.keySet()) {
            Monitor m = monitorMap.get(id);
            String[] identifiers = r.getResourceId().split(":");

            ResourceInfo resourceInfo = envoyResourceManagement
                    .getOne(r.getTenantId(), identifiers[0], identifiers[1]).join().get(0);

            // Make sure to send the event to Kafka
            MonitorEvent monitorEvent = new MonitorEvent()
                    .setFromMonitor(m)
                    .setOperationType(event.getOperation())
                    .setEnvoyId(resourceInfo.getEnvoyId());

            monitorEventProducer.sendMonitorEvent(monitorEvent);
        }
    }
}
