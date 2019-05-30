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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@DataJpaTest(showSql = false)
@AutoConfigureJson
public class BoundMonitorRepositoryTest {

  private static final String MONITOR_TENANT = "monitor-t-1";
  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private BoundMonitorRepository repository;

  private PodamFactory podamFactory = new PodamFactoryImpl();

  @Test
  public void testFindOnesWithoutEnvoy() {
    final Monitor monitor = createMonitor(MONITOR_TENANT, ConfigSelectorScope.LOCAL);

    save(monitor, "z-1", "r-1", null);
    save(monitor, "z-1", "r-2", "e-1");
    save(monitor, "public/1", "r-3", null);
    save(monitor, "public/1", "r-4", "e-2");

    final List<BoundMonitor> t1z1 = repository.findAllWithoutEnvoyInPrivateZone(MONITOR_TENANT, "z-1");
    assertThat(t1z1, hasSize(1));
    assertThat(t1z1.get(0).getResourceId(), equalTo("r-1"));

    final List<BoundMonitor> publicResults = repository.findAllWithoutEnvoyInPublicZone("public/1");
    assertThat(publicResults, hasSize(1));
    assertThat(publicResults.get(0).getResourceId(), equalTo("r-3"));
  }

  @Test
  public void testFindOnesWithEnvoy() {
    final Monitor monitor = createMonitor(MONITOR_TENANT, ConfigSelectorScope.LOCAL);

    save(monitor, "z-1", "r-1", null);
    save(monitor, "z-1", "r-2", "e-1");
    save(monitor, "z-1", "r-3", "e-1");
    save(monitor, "public/1", "r-4", null);
    save(monitor, "public/1", "r-5", "e-1");
    save(monitor, "public/2", "r-6", "e-1");

    final List<BoundMonitor> t1z1 = repository.findWithEnvoyInPrivateZone(
        MONITOR_TENANT, "z-1", "e-1", null);
    assertThat(t1z1, hasSize(2));
    assertThat(t1z1.get(0).getResourceId(), equalTo("r-2"));
    assertThat(t1z1.get(1).getResourceId(), equalTo("r-3"));

    final List<BoundMonitor> publicResults = repository.findWithEnvoyInPublicZone("public/1", "e-1", null);
    assertThat(publicResults, hasSize(1));
    assertThat(publicResults.get(0).getResourceId(), equalTo("r-5"));
  }

  @Test
  public void testFindOnesWithEnvoy_paging() {
    final Monitor monitor = createMonitor(MONITOR_TENANT, ConfigSelectorScope.LOCAL);

    save(monitor, "z-1", "r-1", "e-1");
    save(monitor, "z-1", "r-2", "e-1");
    save(monitor, "z-1", "r-3", "e-1");
    save(monitor, "public/1", "r-4", "e-1");
    save(monitor, "public/1", "r-5", "e-1");
    save(monitor, "public/1", "r-6", "e-1");

    final List<BoundMonitor> t1z1 = repository.findWithEnvoyInPrivateZone(
        MONITOR_TENANT, "z-1", "e-1", PageRequest.of(0, 2));
    assertThat(t1z1, hasSize(2));
    assertThat(t1z1.get(0).getResourceId(), equalTo("r-1"));
    assertThat(t1z1.get(1).getResourceId(), equalTo("r-2"));

    final List<BoundMonitor> publicResults = repository.findWithEnvoyInPublicZone(
        "public/1", "e-1", PageRequest.of(0, 2));
    assertThat(publicResults, hasSize(2));
    assertThat(publicResults.get(0).getResourceId(), equalTo("r-4"));
    assertThat(publicResults.get(1).getResourceId(), equalTo("r-5"));
  }

  private void save(Monitor monitor, String zone, String resource, String envoyId) {
    final Monitor retrievedMonitor = entityManager.find(Monitor.class, monitor.getId());
    assertThat(retrievedMonitor, notNullValue());

    entityManager.persist(new BoundMonitor()
        .setMonitor(monitor)
        .setZoneName(zone)
        .setResourceId(resource)
        .setEnvoyId(envoyId)
    );
    entityManager.flush();
  }

  @Test
  public void testFindAllByMonitor_TenantId() {
    final Monitor monitor = createMonitor(MONITOR_TENANT, ConfigSelectorScope.LOCAL);
    final Monitor otherMonitor = createMonitor("t-some-other", ConfigSelectorScope.LOCAL);

    save(monitor, "z-1", "r-1", null);
    save(monitor, "z-1", "r-2", "e-1");
    save(otherMonitor, "public/1", "r-2", null);
    save(monitor, "z-1", "r-3", "e-1");
    save(monitor, "public/1", "r-4", null);
    save(monitor, "public/1", "r-5", "e-1");
    save(monitor, "public/2", "r-6", "e-1");

    final Page<BoundMonitor> results = repository
        .findAllByMonitor_TenantId(MONITOR_TENANT, PageRequest.of(1, 2));

    assertThat(results.getContent(), hasSize(2));
    assertThat(results.getContent().get(0).getResourceId(), equalTo("r-3"));
    assertThat(results.getContent().get(1).getResourceId(), equalTo("r-4"));
    assertThat(results.getTotalElements(), equalTo(6L));
  }

  @Test
  public void testfindAllByMonitor_IdIn() {
    final Monitor monitor = createMonitor(MONITOR_TENANT, ConfigSelectorScope.LOCAL);
    final Monitor otherMonitor = createMonitor("t-some-other", ConfigSelectorScope.LOCAL);
    final Monitor yetAnotherMonitor = createMonitor("t-yet-another", ConfigSelectorScope.LOCAL);

    save(monitor, "z-1", "r-1", null);
    save(monitor, "z-1", "r-2", null);
    save(otherMonitor, "public/1", "r-3", null);
    save(monitor, "z-1", "r-4", null);
    save(yetAnotherMonitor, "z-1", "r-5", null);

    // EXECUTE

    final List<BoundMonitor> results = repository
        .findAllByMonitor_IdIn(Arrays.asList(monitor.getId(), otherMonitor.getId()));

    // VERIFY

    assertThat(results, hasSize(4));
    assertThat(results.get(0).getResourceId(), equalTo("r-1"));
    assertThat(results.get(1).getResourceId(), equalTo("r-2"));
    assertThat(results.get(2).getResourceId(), equalTo("r-3"));
    assertThat(results.get(3).getResourceId(), equalTo("r-4"));
  }

  @Test
  public void testfindAllByMonitor_IdAndResourceIdIn() {
    final Monitor monitor = createMonitor(MONITOR_TENANT, ConfigSelectorScope.LOCAL);
    final Monitor otherMonitor = createMonitor("t-some-other", ConfigSelectorScope.LOCAL);

    save(monitor, "z-1", "r-1", "e-1");
    save(monitor, "z-2", "r-1", "e-1");
    save(otherMonitor, "z-1", "r-1", "e-1");
    save(monitor, "z-1", "r-2", "e-1");
    save(otherMonitor, "z-1", "r-2", "e-1");
    save(monitor, "z-1", "r-3", "e-1");
    save(otherMonitor, "z-1", "r-3", "e-1");

    final List<BoundMonitor> results = repository
        .findAllByMonitor_IdAndResourceIdIn(monitor.getId(), Arrays.asList("r-1", "r-3"));

    assertThat(results, hasSize(3));
    assertThat(results, containsInAnyOrder(
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setEnvoyId("e-1")
            .setZoneName("z-1"),
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setEnvoyId("e-1")
            .setZoneName("z-2"),
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-3")
            .setEnvoyId("e-1")
            .setZoneName("z-1")
    ));
  }

  @Test
  public void testfindAllByMonitor_IdAndZoneIdIn() {
    final Monitor monitor = createMonitor(MONITOR_TENANT, ConfigSelectorScope.LOCAL);
    final Monitor otherMonitor = createMonitor("t-some-other", ConfigSelectorScope.LOCAL);

    save(monitor, "z-1", "r-1", "e-1");
    save(monitor, "z-2", "r-1", "e-1");
    save(otherMonitor, "z-1", "r-1", "e-1");
    save(monitor, "z-1", "r-2", "e-1");
    save(otherMonitor, "z-1", "r-2", "e-1");
    save(monitor, "z-3", "r-3", "e-1");
    save(otherMonitor, "z-3", "r-3", "e-1");

    final List<BoundMonitor> results = repository
        .findAllByMonitor_IdAndZoneNameIn(monitor.getId(), Arrays.asList("z-1", "z-2"));

    assertThat(results, hasSize(3));
    assertThat(results, containsInAnyOrder(
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setEnvoyId("e-1")
            .setZoneName("z-1"),
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setEnvoyId("e-1")
            .setZoneName("z-2"),
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-2")
            .setEnvoyId("e-1")
            .setZoneName("z-1")
    ));
  }

  @Test
  public void testfindResourceIdsBoundToMonitor() {
    final Monitor monitor = createMonitor(MONITOR_TENANT, ConfigSelectorScope.LOCAL);
    final Monitor otherMonitor = createMonitor("t-some-other", ConfigSelectorScope.LOCAL);

    save(monitor, "z-1", "r-1", "e-1");
    save(monitor, "z-2", "r-1", "e-1");
    save(otherMonitor, "z-1", "r-3", "e-1");
    save(monitor, "z-1", "r-2", "e-1");

    final Set<String> resourceIds =
        repository.findResourceIdsBoundToMonitor(monitor.getId());

    assertThat(resourceIds, containsInAnyOrder("r-1", "r-2"));
  }

  @Test
  public void testFindMonitorsBoundToResource() {

    final List<Monitor> monitors = new ArrayList<>();
    for (int tenantIndex = 0; tenantIndex < 2; tenantIndex++) {
      for (int monitorIndex = 0; monitorIndex < 5; monitorIndex++) {
        final Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
        monitor.setId(null);
        monitor.setTenantId(String.format("t-%d", tenantIndex));
        final Monitor savedMonitor = entityManager.persistFlushFind(monitor);
        monitors.add(savedMonitor);

        for (int boundIndex = 0; boundIndex < 3; boundIndex++) {
          entityManager.persist(
              new BoundMonitor()
                  .setMonitor(savedMonitor)
                  .setZoneName(String.format("z-%d", boundIndex))
                  .setResourceId("r-1")
                  .setRenderedContent(monitor.getContent())
          );
        }
      }
    }

    final List<UUID> monitorIds = repository
        .findMonitorsBoundToResource("t-0", "r-1");

    assertThat(monitorIds, hasSize(5));

    assertThat(monitorIds, containsInAnyOrder(
        monitors.get(0).getId(),
        monitors.get(1).getId(),
        monitors.get(2).getId(),
        monitors.get(3).getId(),
        monitors.get(4).getId()
    ));
  }

  @Test
  public void testfindAllLocalByTenantResource() {
    final Monitor localMonitorT1 = createMonitor("t-1", ConfigSelectorScope.LOCAL);
    final Monitor remoteMonitorT1 = createMonitor("t-1", ConfigSelectorScope.REMOTE);
    final Monitor localMonitorT2 = createMonitor("t-2", ConfigSelectorScope.LOCAL);

    // matches the query of t-1, r-1, local
    entityManager.persist(
        new BoundMonitor()
          .setMonitor(localMonitorT1)
          .setResourceId("r-1")
          .setZoneName("")
          .setRenderedContent("1")
    );
    entityManager.persist(
        new BoundMonitor()
          .setMonitor(localMonitorT1)
          .setResourceId("r-2") // mismatch, other resource
          .setZoneName("")
          .setRenderedContent("2")
    );
    entityManager.persist(
        new BoundMonitor()
          .setMonitor(remoteMonitorT1) // mismatch, remote
          .setResourceId("r-1")
          .setZoneName("public/west")
          .setRenderedContent("3")
    );
    entityManager.persist(
        new BoundMonitor()
          .setMonitor(localMonitorT2) // mismatch, other tenant
          .setResourceId("r-1")
          .setZoneName("")
          .setRenderedContent("4")
    );

    final List<BoundMonitor> results = repository
        .findAllLocalByTenantResource("t-1", "r-1");

    assertThat(results, hasSize(1));
    assertThat(results.get(0).getRenderedContent(), equalTo("1"));
  }

  private Monitor createMonitor(String monitorTenant,
                                ConfigSelectorScope selectorScope) {
    return entityManager.persist(new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(selectorScope)
        .setContent("{}")
        .setTenantId(monitorTenant)
    );
  }
}