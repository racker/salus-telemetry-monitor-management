/*
 * Copyright 2020 Rackspace US, Inc.
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

import static com.google.common.collect.Collections2.transform;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.MetadataPolicy;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.Resource;
import com.rackspace.salus.telemetry.entities.Zone;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.messaging.MetadataPolicyEvent;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MetadataValueType;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.model.TargetClassName;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorPolicyRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import com.rackspace.salus.telemetry.repositories.ResourceRepository;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.persistence.RollbackException;
import javax.transaction.Transactional;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.TransactionSystemException;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
    "salus.services.resourceManagementUrl=http://this-is-a-non-null-value",
    "salus.services.policyManagementUrl=http://this-is-a-non-null-value"
})
@EnableTestContainersDatabase
@AutoConfigureDataJpa
@ContextConfiguration(classes = {
    ObjectMapper.class, MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class,
    MonitorConversionService.class,
    MetadataUtils.class,
    DatabaseConfig.class,
    ZoneAllocationResolverFactory.class,
    SimpleMeterRegistry.class,
    ZonesProperties.class,
    ServicesProperties.class,
})
public class MonitorManagement_MetadataPolicyTest {

  private static final Duration UPDATED_DURATION_VALUE = Duration.ofSeconds(99);
  private static final int UPDATED_COUNT_VALUE = 55;
  private static final String DEFAULT_ENVOY_ID = "env1";
  private static final String DEFAULT_RESOURCE_ID = "os:LINUX";

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @MockBean
  MonitorEventProducer monitorEventProducer;

  @MockBean
  EnvoyResourceManagement envoyResourceManagement;

  @MockBean
  ZoneStorage zoneStorage;

  @MockBean
  BoundMonitorRepository boundMonitorRepository;

  @MockBean
  MonitorPolicyRepository monitorPolicyRepository;

  @MockBean
  ResourceRepository resourceRepository;

  @MockBean
  ResourceApi resourceApi;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  ZoneManagement zoneManagement;

  @MockBean
  PatchHelper patchHelper;

  @MockBean
  ZoneAllocationResolver zoneAllocationResolver;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MonitorRepository monitorRepository;

  @Autowired
  private MonitorManagement monitorManagement;

  @Autowired
  MetadataUtils metadataUtils;

  private PodamFactory podamFactory = new PodamFactoryImpl();

  @Captor
  private ArgumentCaptor<List<BoundMonitor>> captorOfBoundMonitorList;

  @Captor
  private ArgumentCaptor<List<UUID>> captorOfIds;

  @Captor
  private ArgumentCaptor<Monitor> captorOfMonitor;

  @Before
  public void setUp() {
    monitorManagement = Mockito.spy(monitorManagement);

    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setTenantId("abcde")
        .setResourceId(DEFAULT_RESOURCE_ID)
        .setLabels(Collections.singletonMap("os", "LINUX"))
        .setAssociatedWithEnvoy(true)
    );

    ResourceInfo resourceInfo = new ResourceInfo()
        .setTenantId("abcde")
        .setResourceId(DEFAULT_RESOURCE_ID)
        .setLabels(Collections.singletonMap("os", "LINUX"))
        .setEnvoyId(DEFAULT_ENVOY_ID);

    when(envoyResourceManagement.getOne(anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    EnvoyResourcePair pair = new EnvoyResourcePair().setEnvoyId("e-new").setResourceId("r-new-1");

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(Optional.of(pair));

    when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-new", "r-new-1")));
  }

  @After
  public void tearDown() throws Exception {
    // Since this test class is not using @Transactional (and adding that causes test failures)
    // delete content to avoid cross-contaminating MonitorManagementTest
    monitorRepository.deleteAll();
  }

  @Test
  public void createMonitor_withIntervalPolicy() {
    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any(), anyBoolean()))
        .thenReturn(Map.of("interval",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("interval")
                .setValue("PT60S")
                .setValueType(MetadataValueType.DURATION)));

    String tenantId = RandomStringUtils.randomAlphabetic(10);
    MonitorCU create = getBaseMonitorCU();

    Monitor monitor = monitorManagement.createMonitor(tenantId, create);

    assertThat(monitor.getInterval(), equalTo(Duration.ofSeconds(60)));
  }

  @Test
  public void createMonitor_intervalPolicyMissing() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    MonitorCU create = getBaseMonitorCU();

    // RollbackException wraps the ConstraintViolations
    exceptionRule.expect(TransactionSystemException.class);
    exceptionRule.expectCause(IsInstanceOf.instanceOf(RollbackException.class));
    monitorManagement.createMonitor(tenantId, create);
  }

  @Test
  public void testPatchExistingMonitor_allNullValues() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any(), anyBoolean()))
        .thenReturn(Map.of(
            "interval",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("interval")
                .setValue("PT42S")
                .setValueType(MetadataValueType.DURATION)));

    when(policyApi.getDefaultMonitoringZones(anyString(), anyBoolean()))
        .thenReturn(List.of("public/defaultZone1" ,"public/defaultZone2"));
    Resource resource = podamFactory.manufacturePojo(Resource.class);
    when(resourceRepository.findByTenantIdAndResourceId(anyString(), anyString()))
        .thenReturn(Optional.of(resource));

    final Monitor monitor = saveAssortmentOfPingMonitors(tenantId).get(0);

    final BoundMonitor bound1 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId(tenantId)
        .setResourceId("r-1")
        .setEnvoyId("e-new")
        .setZoneName("z-1")
        .setRenderedContent("address=something_else");

    when(boundMonitorRepository.findAllByMonitor_Id(monitor.getId()))
        .thenReturn(List.of(bound1));

    ResourceInfo resourceInfo = new ResourceInfo()
        .setTenantId(tenantId)
        .setResourceId(resource.getResourceId())
        .setEnvoyId(DEFAULT_ENVOY_ID);
    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    // EXECUTE

    // update will contain all null values
    final MonitorCU update = new MonitorCU();
    final Monitor updatedMonitor = monitorManagement.updateMonitor(
        tenantId, monitor.getId(), update, true);

    // VERIFY

    // verify returned entity's field
    assertThat(updatedMonitor.getInterval(), equalTo(Duration.ofSeconds(42)));
    assertThat(updatedMonitor.getMonitorName(), nullValue());
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(LabelSelectorMethod.AND));
    // no zones set, so zone metadata will be used when binding.
    assertThat(updatedMonitor.getZones(), hasSize(0));
    // null gets returned when we store the value, but {} gets retrieved when we do a get (as shown farther below)
    // the null does not get exposed via the api
    assertThat(updatedMonitor.getLabelSelector(), nullValue());

    // and verify the stored entity
    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, updatedMonitor.getId());
    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getInterval(), equalTo(Duration.ofSeconds(42)));
    assertThat(retrieved.get().getZones(), hasSize(0));
    // Retrieving nothing/null from an element collection leads to an empty collection being returned
    assertThat(retrieved.get().getLabelSelector(), equalTo(Collections.emptyMap()));

    // once for interval change and once for zone change
    verify(boundMonitorRepository, times(2)).findAllByMonitor_Id(monitor.getId());
    // one save when adding the extra zone and one for label selector change
    verify(boundMonitorRepository, times(2)).saveAll(any());

    verify(policyApi).getDefaultMonitoringZones(MetadataPolicy.DEFAULT_ZONE, true);

    // only one event is sent
    // since the same envoy is used in the existing bound monitor
    // and in the new binding, it alone will be told to update its config.
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );

    verifyNoMoreInteractions(monitorEventProducer);
  }

  @Test
  public void testPatchExistingMonitor_someValueSetSomeValueNull() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any(), anyBoolean()))
        .thenReturn(Map.of("interval",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("interval")
                .setValue("42")
                .setValueType(MetadataValueType.DURATION),
            "monitorName",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("monitorName")
                .setValue("nameFromPolicy")
                .setValueType(MetadataValueType.STRING)));

    final Monitor monitor = saveAssortmentOfPingMonitors(tenantId).get(0);

    final BoundMonitor bound1 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId(tenantId)
        .setResourceId("r-1")
        .setEnvoyId("e-new")
        .setZoneName("z-1")
        .setRenderedContent("address=something_else");

    when(boundMonitorRepository.findAllByMonitor_Id(monitor.getId()))
        .thenReturn(List.of(bound1));

    List<Zone> zones = monitor.getZones().stream().map(z -> new Zone().setName(z))
        .collect(Collectors.toList());

    when(zoneManagement.getAvailableZonesForTenant(anyString(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    // EXECUTE

    // update will contain empty zones but a new interval
    final MonitorCU update = getBaseMonitorCUFromMonitor(monitor);

    final Duration newInterval = monitor.getInterval().plusSeconds(120L);
    update.setInterval(newInterval);
    update.setMonitorName(null);
    update.setZones(monitor.getZones()); // avoid any zone change logic

    final Monitor updatedMonitor = monitorManagement.updateMonitor(
        tenantId, monitor.getId(), update, true);

    // VERIFY

    // new interval is set using provided value, not metadata
    assertThat(updatedMonitor.getInterval(), equalTo(newInterval));
    // monitorName is set with metadata policy info
    assertThat(updatedMonitor.getMonitorName(), equalTo("nameFromPolicy"));
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(LabelSelectorMethod.AND));
    assertThat(updatedMonitor.getZones(), hasSize(1));

    // and verify the stored entity
    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, updatedMonitor.getId());
    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getInterval(), equalTo(newInterval));
    assertThat(retrieved.get().getMonitorName(), equalTo("nameFromPolicy"));

    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());
    // no fields on the plugin changed so the bound monitor did not need to be re-saved
    verify(boundMonitorRepository, never()).saveAll(any());

    // only one event is sent
    // since the same envoy is used in the existing bound monitor
    // and in the new binding, it alone will be told to update its config.
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );

    verifyNoMoreInteractions(monitorEventProducer);
  }

  @Test
  public void testPatchExistingMonitor_allValuesSet() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    // ensure the provided zone is available to this tenant
    when(zoneManagement.getAvailableZonesForTenant(any(), any()))
        .thenReturn(new PageImpl<>(List.of(new Zone().setName("public/newZone1")), Pageable.unpaged(), 1));

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any(), anyBoolean()))
        .thenReturn(Map.of("zones",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("zones")
                .setValue("public/defaultZone1,public/defaultZone2")
                .setValueType(MetadataValueType.STRING_LIST),
            "interval",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("interval")
                .setValue("42")
                .setValueType(MetadataValueType.DURATION)));

    final Monitor monitor = saveAssortmentOfPingMonitors(tenantId).get(0);

    final BoundMonitor bound1 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId(tenantId)
        .setResourceId("r-1")
        .setEnvoyId("e-new")
        .setZoneName("z-1")
        .setRenderedContent("address=something_else");

    when(boundMonitorRepository.findAllByMonitor_Id(monitor.getId()))
        .thenReturn(List.of(bound1));

    // EXECUTE

    // update will contain new zones and a new interval
    final MonitorCU update = getBaseMonitorCUFromMonitor(monitor);

    final Duration newInterval = monitor.getInterval().plusSeconds(120L);
    update.setInterval(newInterval);
    update.setZones(List.of("public/newZone1"));

    final Monitor updatedMonitor = monitorManagement.updateMonitor(
        tenantId, monitor.getId(), update, true);

    // VERIFY

    // new interval is set using provided value, not metadata
    assertThat(updatedMonitor.getInterval(), equalTo(newInterval));
    assertThat(updatedMonitor.getMonitorName(), nullValue());
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(LabelSelectorMethod.AND));
    assertThat(updatedMonitor.getZones(), hasSize(1));
    // new zones are set with provided value, not metadata
    assertThat(updatedMonitor.getZones(), contains("public/newZone1"));

    // and verify the stored entity
    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, updatedMonitor.getId());
    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getInterval(), equalTo(newInterval));
    assertThat(retrieved.get().getZones(), contains("public/newZone1"));

    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());
    verify(boundMonitorRepository, times(1)).saveAll(any());

    // only one event is sent
    // since the same envoy is used in the existing bound monitor
    // and in the new binding, it alone will be told to update its config.
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );

    verify(zoneManagement).getAvailableZonesForTenant(eq(tenantId), any());

    verifyNoMoreInteractions(monitorEventProducer, zoneManagement);
  }

  @Test
  public void testHandleMetadataPolicyEvent_pluginMetadata() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID policyId = UUID.randomUUID();

    // Returns the list of monitor metadata policies effective on this account
    when(policyApi.getEffectiveMonitorMetadataPolicies(anyString(), anyBoolean()))
        .thenReturn(List.of(
            // The actual RemotePlugin policy that will be used
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("count")
                .setValue(Integer.toString(UPDATED_COUNT_VALUE))
                .setValueType(MetadataValueType.INT)
                .setTargetClassName(TargetClassName.RemotePlugin)
                .setId(policyId),
            // A similar but irrelevant LocalPlugin policy
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("count")
                .setValue("677")
                .setValueType(MetadataValueType.INT)
                .setTargetClassName(TargetClassName.LocalPlugin)
                .setId(UUID.randomUUID()),
            // A similar but irrelevant Monitor policy
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("interval")
                .setValue(Long.toString(UPDATED_DURATION_VALUE.getSeconds()))
                .setValueType(MetadataValueType.DURATION)
                .setTargetClassName(TargetClassName.Monitor)
                .setId(UUID.randomUUID())));


    List<Monitor> savedMonitors = saveAssortmentOfPingMonitors(tenantId);
    List<Monitor> monitorsThatUseMetadata = savedMonitors.stream().filter(
        m -> !m.getPluginMetadataFields().isEmpty())
        .collect(Collectors.toList());
    List<UUID> monitorIds = monitorsThatUseMetadata.stream().map(Monitor::getId).collect(Collectors.toList());

    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(any(),any(),any())).thenReturn(
        Page.empty());

    monitorManagement.handleMetadataPolicyEvent((MetadataPolicyEvent) new MetadataPolicyEvent()
        .setTenantId(tenantId)
        .setPolicyId(policyId));

    List<Monitor> updatedMonitors = monitorManagement.getMonitors(tenantId, Pageable.unpaged()).getContent();

    // The below tests that the "count" was updated where applicable.
    // Interval is not updated even though it is a policy field since this metadata event did not relate to it.

    String expectedPolicyUpdateContent = "{\"type\":\"ping\",\"target\":\"localhost\",\"count\":" + UPDATED_COUNT_VALUE
        + ",\"pingInterval\":null,\"timeout\":null,\"deadline\":null}";
    // due to content being stored as a string and the details of unmodified monitors are not regenerated,
    // for the purpose of this test all the `null` values seen in te updated monitor are excluded here.
    String expectedOriginalContent = "{\"type\":\"ping\",\"target\":\"localhost\",\"count\":72}";

    // DB query doesn't impose any particular ordering, but we need to simplify assertions to be
    // deterministically ordered
    final List<Monitor> sortedResults = updatedMonitors.stream()
        .sorted(Comparator.comparing(Monitor::getContent).thenComparing(Monitor::getInterval))
        .collect(Collectors.toList());

    // has a custom count value, but count was in its pluginMetadata so it should now be
    // using the policy metadata value
    assertThat(sortedResults.get(0).getContent(), equalTo(expectedPolicyUpdateContent));
    assertThat(sortedResults.get(0).getInterval(), equalTo(Duration.ofSeconds(0)));
    // ha3 the count set to the metadata value and count was in its pluginMetadata
    // so it should still have that value
    assertThat(sortedResults.get(1).getContent(), equalTo(expectedPolicyUpdateContent));
    assertThat(sortedResults.get(1).getInterval(), equalTo(UPDATED_DURATION_VALUE));
    // has a custom count with no pluginMetadata so should still have that value
    assertThat(sortedResults.get(2).getContent(), equalTo(expectedOriginalContent));
    assertThat(sortedResults.get(2).getInterval(), equalTo(Duration.ofSeconds(60)));

    // verify the unbind / bind process happened
    verify(monitorManagement).unbindByTenantAndMonitorId(eq(tenantId), captorOfIds.capture());
    assertThat(captorOfIds.getValue(), containsInAnyOrder(monitorIds.toArray()));

    verify(monitorManagement, times(monitorsThatUseMetadata.size())).bindMonitor(
        eq(tenantId),
        captorOfMonitor.capture(),
        eq(List.of("public/z-1")));
    assertThat(transform(captorOfMonitor.getAllValues(), Monitor::getId), containsInAnyOrder(monitorIds.toArray()));
  }

  /**
   * Same as the above test except it verifies the Monitor interval metadata policy
   * was used, and the "count" policy had no effect.
   */
  @Test
  public void testHandleMetadataPolicyEvent_monitorMetadata() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID policyId = UUID.randomUUID();

    // Returns the list of monitor metadata policies effective on this account
    when(policyApi.getEffectiveMonitorMetadataPolicies(anyString(), anyBoolean()))
        .thenReturn(List.of(
            // An irrelevant RemotePlugin policy
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("count")
                .setValue(Integer.toString(UPDATED_COUNT_VALUE))
                .setValueType(MetadataValueType.INT)
                .setTargetClassName(TargetClassName.RemotePlugin)
                .setId(UUID.randomUUID()),
            // An irrelevant LocalPlugin policy
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("count")
                .setValue("677")
                .setValueType(MetadataValueType.INT)
                .setTargetClassName(TargetClassName.LocalPlugin)
                .setId(UUID.randomUUID()),
            // The actual RemotePlugin policy that will be used
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("interval")
                .setValue(UPDATED_DURATION_VALUE.toString())
                .setValueType(MetadataValueType.DURATION)
                .setTargetClassName(TargetClassName.Monitor)
                .setId(policyId)));


    List<Monitor> savedMonitors = saveAssortmentOfPingMonitors(tenantId);
    List<Monitor> monitorsThatUseMetadata = savedMonitors.stream().filter(
        m -> !m.getMonitorMetadataFields().isEmpty())
        .collect(Collectors.toList());
    List<UUID> monitorIds = monitorsThatUseMetadata.stream().map(Monitor::getId).collect(Collectors.toList());

    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(any(),any(),any())).thenReturn(
        Page.empty());
    monitorManagement.handleMetadataPolicyEvent((MetadataPolicyEvent) new MetadataPolicyEvent()
        .setTenantId(tenantId)
        .setPolicyId(policyId));

    // verify two monitors have the interval set by metadata and one has its pre-existing hard set value
    List<Monitor> updatedMonitors = monitorManagement.getMonitors(tenantId, Pageable.unpaged()).getContent();
    Map<Duration, List<Monitor>> intervals = updatedMonitors.stream().collect(Collectors.groupingBy(Monitor::getInterval));
    assertThat(updatedMonitors, hasSize(3));
    assertThat(intervals.keySet(), hasSize(2));
    assertThat(intervals.get(UPDATED_DURATION_VALUE), hasSize(2));
    assertThat(intervals.get(Duration.ofSeconds(60)), hasSize(1));

    // verify the unbind / bind process happened
    verify(monitorManagement).unbindByTenantAndMonitorId(eq(tenantId), captorOfIds.capture());
    assertThat(captorOfIds.getValue(), containsInAnyOrder(monitorIds.toArray()));

    verify(monitorManagement, times(monitorsThatUseMetadata.size())).bindMonitor(
        eq(tenantId),
        captorOfMonitor.capture(),
        eq(List.of("public/z-1")));
    assertThat(transform(captorOfMonitor.getAllValues(), Monitor::getId), containsInAnyOrder(monitorIds.toArray()));
  }

  /**
   * Same as the above test except it verifies the Monitor interval metadata policy
   * was used, and the "count" policy had no effect.
   */
  @Test
  public void testHandleMetadataPolicyEvent_zoneMetadata() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID policyId = UUID.randomUUID();

    // Returns the list of monitor metadata policies effective on this account
    when(policyApi.getEffectiveMonitorMetadataPolicies(anyString(), anyBoolean()))
        .thenReturn(List.of(
            // A zone policy
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(null)
                .setKey(MetadataPolicy.ZONE_METADATA_PREFIX + MetadataPolicy.DEFAULT_ZONE)
                .setValue(String.join(",", "zone1", "zone2"))
                .setValueType(MetadataValueType.STRING_LIST)
                .setTargetClassName(TargetClassName.RemotePlugin)
                .setId(policyId)));

    // store one monitor using zone policy and one with set zones
    Monitor monitorUsingPolicy = monitorRepository.save(new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.ping)
        .setContent("{\"type\": \"ping\", \"target\": \"localhost\"}")
        .setTenantId(tenantId)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setZones(Collections.emptyList())

        .setLabelSelector(Collections.emptyMap())
        .setInterval(Duration.ofSeconds(60)));

    Monitor monitorNotUsingPolicy = monitorRepository.save(new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.ping)
        .setContent("{\"type\":\"ping\",\"target\":\"localhost\"}")
        .setTenantId(tenantId)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setZones(Collections.singletonList("public/z-1"))

        .setLabelSelector(Collections.emptyMap())
        .setInterval(Duration.ofSeconds(60)));

    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(any(),any(),any())).thenReturn(
        Page.empty());

    monitorManagement.handleMetadataPolicyEvent((MetadataPolicyEvent) new MetadataPolicyEvent()
        .setTenantId(tenantId)
        .setPolicyId(policyId));


    // the zones field on a monitor should not be modified when zone metadata changes
    Monitor updatedMonitor = monitorManagement.getMonitor(tenantId, monitorUsingPolicy.getId()).orElseThrow();
    assertThat(updatedMonitor.getZones(), hasSize(0));
    Monitor notUpdatedMonitor = monitorManagement.getMonitor(tenantId, monitorNotUsingPolicy.getId()).orElseThrow();
    assertThat(notUpdatedMonitor.getZones(), hasSize(1));
    assertThat(notUpdatedMonitor.getZones(), contains("public/z-1"));

    // verify the unbind / bind process happened.  Only one monitor is updated.
    verify(monitorManagement).unbindByTenantAndMonitorId(tenantId, List.of(monitorUsingPolicy.getId()));
    verify(monitorManagement).bindMonitor(eq(tenantId), captorOfMonitor.capture(), eq(Collections.emptyList()));
    assertThat(captorOfMonitor.getValue().getId(), equalTo(monitorUsingPolicy.getId()));
    // verify a call was made to get new monitoring zones
    verify(monitorManagement).determineMonitoringZones((Monitor) any(), any());
  }

  private MonitorCU getBaseMonitorCU() {
    return new MonitorCU()
        .setMonitorType(MonitorType.cpu)
        .setAgentType(AgentType.TELEGRAF)
        .setLabelSelector(Collections.emptyMap())
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setMonitorName(RandomStringUtils.randomAlphabetic(10))
        .setContent("{}")
        .setPluginMetadataFields(null)
        .setResourceId(null)
        .setZones(null)
        .setInterval(null);
  }

  private MonitorCU getBaseMonitorCUFromMonitor(Monitor monitor) {
    return new MonitorCU()
        .setResourceId(monitor.getResourceId())
        .setMonitorName(monitor.getMonitorName())
        .setMonitorType(monitor.getMonitorType())
        .setLabelSelector(monitor.getLabelSelector())
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setContent(monitor.getContent())
        .setInterval(monitor.getInterval())
        .setAgentType(monitor.getAgentType())
        .setSelectorScope(monitor.getSelectorScope())
        .setZones(monitor.getZones())
        .setPluginMetadataFields(monitor.getPluginMetadataFields());
  }

  private List<Monitor> saveAssortmentOfPingMonitors(String tenantId) {
    List<Monitor> monitors = Arrays.asList(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("{\"type\": \"ping\", \"target\": \"localhost\"}")
            .setTenantId(tenantId)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(0))
            .setPluginMetadataFields(List.of("count"))
            .setMonitorMetadataFields(List.of("interval")),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("{\"type\":\"ping\",\"target\":\"localhost\",\"count\":72}")
            .setTenantId(tenantId)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60))
            .setPluginMetadataFields(Collections.emptyList())
            .setMonitorMetadataFields(Collections.emptyList()),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("{\"type\":\"ping\",\"target\":\"localhost\",\"count\":" + UPDATED_COUNT_VALUE + "}")
            .setTenantId(tenantId)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(UPDATED_DURATION_VALUE)
            .setPluginMetadataFields(List.of("count"))
            .setMonitorMetadataFields(List.of("interval"))
    );

    return Lists.newArrayList(monitorRepository.saveAll(monitors));
  }

  @Test
  @Transactional
  public void testCloneMonitor_usingMetadata() throws JsonProcessingException {
    String originalTenant = RandomStringUtils.randomAlphanumeric(10);
    String newTenant = RandomStringUtils.randomAlphanumeric(10);

    Duration originalInterval = Duration.ofSeconds(300);
    Duration newInterval = Duration.ofSeconds(700);
    int originalCount = 1;
    int newCount = 123;

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any(), anyBoolean()))
        .thenReturn(Map.of("count",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("count")
                .setValue(String.valueOf(newCount))
                .setValueType(MetadataValueType.INT),
            "interval",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("interval")
                .setValue(newInterval.toString())
                .setValueType(MetadataValueType.DURATION)));

    Ping plugin = new Ping().setCount(originalCount);

    Monitor monitor = new Monitor()
        .setTenantId(originalTenant)
        .setLabelSelector(Collections.emptyMap())
        .setExcludedResourceIds(Collections.emptySet())
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setMonitorType(MonitorType.ping)
        .setContent(objectMapper.writeValueAsString(plugin))
        .setInterval(originalInterval)
        .setMonitorMetadataFields(List.of("interval"))
        .setPluginMetadataFields(List.of("count"))
        .setZones(Collections.singletonList("public/z-1"));
    monitor = monitorRepository.save(monitor);

    final ResourceDTO resourceDto = new ResourceDTO()
        .setLabels(Collections.emptyMap())
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setAssociatedWithEnvoy(true)
        .setTenantId(newTenant);
    when(resourceApi.getResourcesWithLabels(anyString(), any(), any()))
        .thenReturn(List.of(resourceDto));

    Monitor clonedMonitor = monitorManagement.cloneMonitor(originalTenant, newTenant, monitor.getId());

    org.assertj.core.api.Assertions.assertThat(Collections.singleton(clonedMonitor))
        .usingElementComparatorIgnoringFields("createdTimestamp", "updatedTimestamp",
            "id", "tenantId", "content", "monitorMetadataFields", "pluginMetadataFields",
            "interval", "zones")
        .containsExactly(monitor);

    // verify all lists are cloned correctly

    assertThat(clonedMonitor.getZones(), hasSize(1));
    Assertions.assertThat(clonedMonitor.getZones()).containsExactlyInAnyOrderElementsOf(
        monitor.getZones());

    assertThat(clonedMonitor.getMonitorMetadataFields(), hasSize(1));
    Assertions.assertThat(clonedMonitor.getMonitorMetadataFields()).containsExactlyInAnyOrderElementsOf(
        monitor.getMonitorMetadataFields());

    assertThat(clonedMonitor.getPluginMetadataFields(), hasSize(1));
    Assertions.assertThat(clonedMonitor.getPluginMetadataFields()).containsExactlyInAnyOrderElementsOf(
        monitor.getPluginMetadataFields());

    // verify modified fields were updated correctly

    Ping updatedPlugin = new Ping().setCount(newCount);
    assertThat(clonedMonitor.getContent(), equalTo(objectMapper.writeValueAsString(updatedPlugin)));
    assertThat(clonedMonitor.getTenantId(), equalTo(newTenant));
    assertThat(clonedMonitor.getId(), notNullValue());
    assertThat(clonedMonitor.getId(), not(monitor.getId()));

    // verify monitors were bound correctly

    verify(boundMonitorRepository).saveAll(Collections.singletonList(
        new BoundMonitor()
            .setMonitor(clonedMonitor)
            .setTenantId(newTenant)
            .setResourceId(resourceDto.getResourceId())
            .setEnvoyId("e-new")
            .setPollerResourceId("r-new-1")
            .setRenderedContent(objectMapper.writeValueAsString(updatedPlugin))
            .setZoneName("public/z-1")
    ));
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );
    verifyNoMoreInteractions(boundMonitorRepository, monitorEventProducer);
  }

  @Test
  @Transactional
  public void testCloneMonitor_monitorTiedToPolicy() throws JsonProcessingException {
    String originalTenant = RandomStringUtils.randomAlphanumeric(10);
    String newTenant = RandomStringUtils.randomAlphanumeric(10);

    Monitor monitor = new Monitor()
        .setTenantId(originalTenant)
        .setLabelSelector(Collections.emptyMap())
        .setExcludedResourceIds(Collections.emptySet())
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setMonitorType(MonitorType.ping)
        .setContent(objectMapper.writeValueAsString(new Ping()))
        .setInterval(Duration.ofSeconds(60))
        .setMonitorMetadataFields(List.of("interval"))
        .setPluginMetadataFields(List.of("count"))
        .setZones(Collections.singletonList("public/z-1"))
        .setPolicyId(UUID.randomUUID());
    monitor = monitorRepository.save(monitor);

    final UUID monitorId = monitor.getId();

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> monitorManagement.cloneMonitor(originalTenant, newTenant, monitorId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot clone monitor tied to a policy");
  }
}
