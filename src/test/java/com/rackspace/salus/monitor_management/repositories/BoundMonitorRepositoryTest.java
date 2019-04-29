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
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.Monitor;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@DataJpaTest(showSql = false)
@AutoConfigureJson
public class BoundMonitorRepositoryTest {

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private BoundMonitorRepository repository;

  @Test
  public void testFindOnesWithoutEnvoy() {
    final Monitor monitor = createMonitor();

    save(monitor, "t-1", "z-1", "r-1", null);
    save(monitor, "t-1", "z-1", "r-2", "e-1");
    save(monitor, "", "public/1", "r-3", null);
    save(monitor, "", "public/1", "r-4", "e-2");

    final List<BoundMonitor> t1z1 = repository.findAllWithoutEnvoy("t-1", "z-1");
    assertThat(t1z1, hasSize(1));
    assertThat(t1z1.get(0).getResourceId(), equalTo("r-1"));

    final List<BoundMonitor> publicResults = repository.findAllWithoutEnvoy("", "public/1");
    assertThat(publicResults, hasSize(1));
    assertThat(publicResults.get(0).getResourceId(), equalTo("r-3"));
  }

  private Monitor createMonitor() {
    return entityManager.persist(new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("{}")
        .setTenantId("monitor-t-1")
    );
  }

  @Test
  public void testFindOnesWithEnvoy() {
    final Monitor monitor = createMonitor();

    save(monitor, "t-1", "z-1", "r-1", null);
    save(monitor, "t-1", "z-1", "r-2", "e-1");
    save(monitor, "t-1", "z-1", "r-3", "e-1");
    save(monitor, "", "public/1", "r-4", null);
    save(monitor, "", "public/1", "r-5", "e-1");
    save(monitor, "", "public/2", "r-6", "e-1");

    final List<BoundMonitor> t1z1 = repository.findAllWithEnvoy("t-1", "z-1", "e-1");
    assertThat(t1z1, hasSize(2));
    assertThat(t1z1.get(0).getResourceId(), equalTo("r-2"));
    assertThat(t1z1.get(1).getResourceId(), equalTo("r-3"));

    final List<BoundMonitor> publicResults = repository.findAllWithEnvoy("", "public/1", "e-1");
    assertThat(publicResults, hasSize(1));
    assertThat(publicResults.get(0).getResourceId(), equalTo("r-5"));
  }

  private void save(Monitor monitor, String tenant, String zone, String resource, String envoyId) {
    final Monitor retrievedMonitor = entityManager.find(Monitor.class, monitor.getId());
    assertThat(retrievedMonitor, notNullValue());

    entityManager.persist(new BoundMonitor()
        .setMonitor(monitor)
        .setZoneTenantId(tenant)
        .setZoneId(zone)
        .setResourceId(resource)
        .setEnvoyId(envoyId)
    );
    entityManager.flush();
  }
}