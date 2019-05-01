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

import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface BoundMonitorRepository extends CrudRepository<BoundMonitor, BoundMonitor.PrimaryKey> {

  List<BoundMonitor> findAllByEnvoyId(String envoyId);

  @Query("select b from BoundMonitor b where b.zoneTenantId = :zoneTenantId and b.zoneId = :zoneId and b.envoyId is null")
  List<BoundMonitor> findAllWithoutEnvoy(String zoneTenantId, String zoneId);

  @Query("select b from BoundMonitor b where b.zoneTenantId = :zoneTenantId and b.zoneId = :zoneId and b.envoyId = :envoyId")
  List<BoundMonitor> findAllWithEnvoy(String zoneTenantId, String zoneId, String envoyId);

  List<BoundMonitor> findAllByMonitor_IdAndResourceId(UUID monitorId, String resourceId);

  Page<BoundMonitor> findAllByMonitor_TenantId(String tenantId, Pageable pageable);
}
