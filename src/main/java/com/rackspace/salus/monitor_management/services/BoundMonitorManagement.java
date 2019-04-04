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

import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.web.model.MonitorCreate;
import com.rackspace.salus.monitor_management.web.model.MonitorUpdate;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.messaging.MonitorEvent;
import com.rackspace.salus.telemetry.messaging.OperationType;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.*;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.validation.Valid;
import java.util.*;
import java.util.stream.Stream;


@Slf4j
@Service
public class BoundMonitorManagement {
    private final BoundMonitorRepository boundMonitorRepository;

    @Autowired
    public BoundMonitorManagement(BoundMonitorRepository boundMonitorRepository) {
        this.boundMonitorRepository = boundMonitorRepository;
    }

    public void createMonitor() throws IllegalArgumentException, AlreadyExistsException {

        UUID uuid = UUID.randomUUID();
        BoundMonitor bm = new BoundMonitor().setEnvoyId("e1").setMonitorId(uuid).setResourceId("r1").setTenantId("t1").setZone("z1");
        BoundMonitor bm2 = new BoundMonitor().setEnvoyId("e2").setMonitorId(uuid).setResourceId("r2").setTenantId("t2").setZone("z2");


        boundMonitorRepository.save(bm);
        boundMonitorRepository.save(bm2);
    }
}
