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
package com.rackspace.salus.monitor_management.repositories;

import com.rackspace.salus.monitor_management.entities.Zone;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneRepository extends PagingAndSortingRepository<Zone, UUID> {
    Optional<Zone> findByTenantIdAndName(String tenantId, String name);

    List<Zone> findAllByTenantId(String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);
}