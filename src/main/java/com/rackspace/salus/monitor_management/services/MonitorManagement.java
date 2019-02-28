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

import com.rackspace.salus.monitor_management.web.model.MonitorCreate;
import com.rackspace.salus.monitor_management.web.model.MonitorUpdate;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.messaging.MonitorEvent;
import com.rackspace.salus.telemetry.messaging.OperationType;
import com.rackspace.salus.telemetry.model.*;
import com.rackspace.salus.telemetry.model.Monitor;
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
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Root;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import java.util.Set;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;


@Slf4j
@Service
public class MonitorManagement {
    private final MonitorEventProducer monitorEventProducer;

    private final MonitorRepository monitorRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    private final EnvoyResourceManagement envoyResourceManagement;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    public MonitorManagement(MonitorRepository monitorRepository, EntityManager entityManager, EnvoyResourceManagement envoyResourceManagement, MonitorEventProducer monitorEventProducer) {
        this.monitorRepository = monitorRepository;
        this.entityManager = entityManager;
        this.envoyResourceManagement = envoyResourceManagement;
        this.monitorEventProducer = monitorEventProducer;
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

        // Make sure to send the event to Kafka
        MonitorEvent monitorEvent = new MonitorEvent()
                .setFromMonitor(monitor)
                .setOperationType(OperationType.CREATE);

        monitorEventProducer.sendMonitorEvent(monitorEvent);
        return monitor;
    }
    void publishMonitor(Monitor monitor, OperationType operationType) {
        List<Resource> resources = new ArrayList<>();
        for (Resource r: resources) {
            String[] identifiers = r.getResourceId().split(":");

            ResourceInfo resourceInfo = envoyResourceManagement
                    .getOne(monitor.getTenantId(), identifiers[0], identifiers[1]).join().get(0);



        }
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

        // Make sure to send the event to Kafka
        MonitorEvent monitorEvent = new MonitorEvent()
                .setFromMonitor(monitor)
                .setOperationType(OperationType.UPDATE);
        monitorEventProducer.sendMonitorEvent(monitorEvent);
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

            // Make sure to send the event to Kafka
            MonitorEvent monitorEvent = new MonitorEvent()
                    .setFromMonitor(monitor)
                    .setOperationType(OperationType.DELETE);
            monitorEventProducer.sendMonitorEvent(monitorEvent);
        } else {
            throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
                    id, tenantId));
        }
    }

    public void handleResourceEvent(ResourceEvent event) {
        log.debug("");
        if (event.getOldLabels() != null) {
            Set<String> keys = event.getOldLabels().keySet();
            keys.removeAll(event.getResource().getLabels().keySet());//now we should have the difference of labels.
        }
        /*
            We probably want to grab three different lists of labels. Deleted labels (set difference on the oldLabels),
            added labels (set difference on the new labels), and the labels that stayed the same (possibly updated?)

            Unless we just want to start out by reading in the new list of labels and clobbering the old data that exists.

            When we do something with them this feels a little like a state machine which is really well suited to functions
            attached to enums. But its probably fine to just grab the different lists and pass them off to their respective SQL
            functions
        */


        // post kafka egress event. This will probably be handled post CRUD event, and not in this function.
        MonitorEvent monitorEvent = new MonitorEvent();
        monitorEvent.setTenantId(event.getResource().getTenantId());
        monitorEvent.setOperationType(event.getOperation());
        // monitorEvent.setEnvoyId()
        // monitorEvent.setAmbassadorId()
        AgentConfig config = new AgentConfig();
        config.setLabels(event.getResource().getLabels());
        config.setContent("this is sample content");
        monitorEvent.setConfig(config);
        monitorEventProducer.sendMonitorEvent(monitorEvent);
    }
}
