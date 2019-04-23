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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.telemetry.model.AgentType;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@DataJpaTest(showSql = true)
@AutoConfigureJson
public class BoundMonitorRepositoryTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private BoundMonitorRepository repository;

  @Test
  public void testFindOnesWithoutEnvoy() {
    final UUID m1 = UUID.randomUUID();

    save(m1, "t-1", "z-1", "r-1", null, "t-1");
    save(m1, "t-1", "z-1", "r-2", "e-1", "t-2");
    save(m1, "", "pz-1", "r-3", null, "t-3");
    save(m1, "", "pz-1", "r-4", "e-2", "t-4");

    final List<BoundMonitor> t1z1 = repository.findOnesWithoutEnvoy("t-1", "z-1");
    assertThat(t1z1, hasSize(1));
    assertThat(t1z1.get(0).getResourceId(), equalTo("r-1"));
  }

  private void save(UUID monitorId, String tenant, String zone, String resource, String envoyId,
                    String targetTenantId) {
    entityManager.persist(new BoundMonitor()
        .setMonitorId(monitorId)
        .setZoneTenantId(tenant)
        .setZoneId(zone)
        .setResourceId(resource)
        .setEnvoyId(envoyId)
        .setTargetTenant(targetTenantId)
        .setAgentType(AgentType.TELEGRAF)
    );
  }
}