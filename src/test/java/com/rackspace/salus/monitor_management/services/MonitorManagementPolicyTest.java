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

import static com.rackspace.salus.telemetry.entities.Monitor.POLICY_TENANT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.errors.DeletionNotAllowedException;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.telegraf.Cpu;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.policy.manage.web.model.MonitorPolicyDTO;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.MonitorPolicy;
import com.rackspace.salus.telemetry.entities.Zone;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.MonitorPolicyEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.PolicyScope;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorPolicyRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.persistence.EntityManager;
import javax.validation.ConstraintViolationException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@EnableTestContainersDatabase
@DataJpaTest(showSql = false)
@Import({ServicesProperties.class, ObjectMapper.class, MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class,
    MonitorConversionService.class,
    MetadataUtils.class,
    DatabaseConfig.class,
    SimpleMeterRegistry.class,
    ZoneAllocationResolverFactory.class,
})
@TestPropertySource(properties = {
    "salus.services.resourceManagementUrl=http://this-is-a-non-null-value",
    "salus.services.policyManagementUrl=http://this-is-a-non-null-value"
})
public class MonitorManagementPolicyTest {

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
  ResourceApi resourceApi;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  PatchHelper patchHelper;

  @MockBean
  ZoneManagement zoneManagement;

  @MockBean
  MetadataUtils metadataUtils;

  @MockBean
  MonitorConversionService monitorConversionService;

  @MockBean
  ZoneAllocationResolver zoneAllocationResolver;

  @Autowired
  EntityManager entityManager;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MonitorRepository monitorRepository;

  @Autowired
  private MonitorManagement monitorManagement;

  private PodamFactory podamFactory = new PodamFactoryImpl();

  private Monitor currentMonitor;

  @Captor
  private ArgumentCaptor<List<BoundMonitor>> captorOfBoundMonitorList;

  @Captor
  private ArgumentCaptor<Monitor> captorOfMonitor;

  @TestConfiguration
  public static class Config {

    @Bean
    public ZonesProperties zonesProperties() {
      return new ZonesProperties();
    }

    @Bean
    public ServicesProperties servicesProperties() {
      return new ServicesProperties()
          .setResourceManagementUrl("");
    }
  }

  @Before
  public void setUp() throws JsonProcessingException {
    Monitor monitor = new Monitor()
        .setTenantId(POLICY_TENANT)
        .setMonitorName("policy_mon1")
        .setMonitorType(MonitorType.ping)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setContent(objectMapper.writeValueAsString(new Cpu()))
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setInterval(Duration.ofSeconds(60));
    currentMonitor = monitorRepository.save(monitor);

    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setResourceId(DEFAULT_RESOURCE_ID)
        .setLabels(Collections.singletonMap("os", "LINUX"))
        .setAssociatedWithEnvoy(true)
    );

    when(resourceApi.getResourcesWithLabels(anyString(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    EnvoyResourcePair pair = new EnvoyResourcePair().setEnvoyId("e-new").setResourceId("r-new-1");

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(Optional.of(pair));

    ResourceInfo resourceInfo = new ResourceInfo()
        .setResourceId("r-1")
        .setEnvoyId("e-1");
    when(envoyResourceManagement.getOne(anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-new", "r-new-1")));
  }

  @Test
  public void testGetMonitorTemplate() {
    Optional<Monitor> m = monitorManagement.getMonitorTemplate(currentMonitor.getId());

    assertTrue(m.isPresent());
    assertThat(m.get().getId(), notNullValue());
    assertThat(m.get().getLabelSelector(), hasEntry("os", "LINUX"));
    assertThat(m.get().getContent(), equalTo(currentMonitor.getContent()));
    assertThat(m.get().getAgentType(), equalTo(currentMonitor.getAgentType()));

    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  @Test
  public void testGetAllMonitorTemplate() {
    Random random = new Random();
    int totalMonitors = random.nextInt(150 - 50) + 50;
    int pageSize = 10;

    Pageable page = PageRequest.of(0, pageSize);
    Page<Monitor> result = monitorManagement.getAllMonitorTemplates(page);

    // There is already one monitor created as default
    assertThat(result.getTotalElements(), equalTo(1L));

    // Create a bunch of monitors using templates (one less to account for the default one)
    createMonitorsForTenant(totalMonitors - 1, POLICY_TENANT);
    // and a few more account level monitors
    createMonitors(10);

    page = PageRequest.of(0, 10);
    result = monitorManagement.getAllMonitorTemplates(page);

    assertThat(result.getTotalElements(), equalTo((long) totalMonitors));
    assertThat(result.getTotalPages(), equalTo((totalMonitors + pageSize - 1) / pageSize));
  }

  @Test
  public void testCreateMonitorTemplate() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setMonitorType(MonitorType.cpu);
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setZones(null);
    create.setInterval(Duration.ofSeconds(60));
    create.setResourceId(null);

    Monitor returned = monitorManagement.createMonitorTemplate(create);

    assertThat(returned.getTenantId(), equalTo(POLICY_TENANT));
    assertThat(returned.getId(), notNullValue());
    assertThat(returned.getMonitorName(), equalTo(create.getMonitorName()));
    assertThat(returned.getContent(), equalTo(create.getContent()));
    assertThat(returned.getAgentType(), equalTo(create.getAgentType()));
    assertThat(returned.getLabelSelectorMethod(), equalTo(create.getLabelSelectorMethod()));

    assertThat(returned.getLabelSelector().size(), greaterThan(0));
    assertTrue(Maps.difference(create.getLabelSelector(), returned.getLabelSelector()).areEqual());

    Optional<Monitor> retrieved = monitorManagement.getMonitorTemplate(returned.getId());

    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getMonitorName(), equalTo(returned.getMonitorName()));
    assertTrue(Maps.difference(returned.getLabelSelector(), retrieved.get().getLabelSelector())
        .areEqual());

    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  @Test
  public void testCreateMonitorTemplate_setResourceId() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setZones(null);
    create.setResourceId(RandomStringUtils.randomAlphabetic(10));

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("Monitor Templates must use label selectors and not a resourceId");
    monitorManagement.createMonitorTemplate(create);
  }

  @Test
  public void testUpdateMonitorTemplate() {
    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("original content")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.OR)
            .setInterval(Duration.ofSeconds(60))
            .setMonitorMetadataFields(Collections.emptyList())
            .setPluginMetadataFields(Collections.emptyList()));

    MonitorCU update = new MonitorCU()
        .setContent("new content")
        .setZones(Collections.singletonList("z-2"))
        .setInterval(Duration.ofSeconds(20));

    // make sure the zones we're setting are allowed to be used
    List<Zone> zones = Arrays.asList(
        new Zone().setName("z-1"),
        new Zone().setName("z-2")
    );
    when(zoneManagement.getAvailableZonesForTenant(anyString(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    // EXECUTE

    final Monitor updatedMonitor = monitorManagement.updateMonitorTemplate(monitor.getId(), update);

    // VERIFY

    // The returned monitor should match the original with zone and content fields changed
    org.assertj.core.api.Assertions.assertThat(Collections.singleton(updatedMonitor))
        .usingElementComparatorIgnoringFields("createdTimestamp", "updatedTimestamp",
            "monitorMetadataFields", "pluginMetadataFields")
        .containsExactly(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setMonitorType(MonitorType.ping)
                .setContent("new content")
                .setTenantId(POLICY_TENANT)
                .setSelectorScope(ConfigSelectorScope.REMOTE)
                .setLabelSelector(monitor.getLabelSelector())
                .setZones(Collections.singletonList("z-2"))
                .setLabelSelectorMethod(LabelSelectorMethod.OR)
                .setInterval(Duration.ofSeconds(20)));

    // these are returned as PersistentBags so cannot be compared to other lists.
    // rather than including them in the above assert, we just verify they are empty here.
    assertThat(updatedMonitor.getMonitorMetadataFields(), hasSize(0));
    assertThat(updatedMonitor.getPluginMetadataFields(), hasSize(0));

    // specified zones were verified
    verify(zoneManagement).getAvailableZonesForTenant(POLICY_TENANT, Pageable.unpaged());

    // No bound monitors will be altered yet.
    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  @Test
  public void testUpdateMonitorTemplate_setResourceId() {
    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("original content")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND));

    MonitorCU update = new MonitorCU()
        .setContent("new content")
        .setZones(Collections.singletonList("z-2"))
        .setResourceId(RandomStringUtils.randomAlphabetic(10));

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("Monitor Templates must use label selectors and not a resourceId");
    monitorManagement.updateMonitorTemplate(monitor.getId(), update);
  }

  @Test
  public void testPatchMonitorTemplate_withMetadata() {
    // make sure the zone we're setting is allowed to be used by this tenant
    List<Zone> zones = Collections.singletonList(new Zone().setName("public/z-1"));
    when(zoneManagement.getAvailableZonesForTenant(anyString(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{\"type\": \"ping\", \"urls\": [\"localhost\"]}")
            .setMonitorType(MonitorType.ping)
            .setTenantId(POLICY_TENANT)
            .setInterval(Duration.ofSeconds(60))
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND));

    // this has to be populated with all fields otherwise they will be considered as null
    MonitorCU update = new MonitorCU()
        .setZones(null)
        .setInterval(null) // setting this should trigger a failure on update
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setAgentType(monitor.getAgentType())
        .setMonitorType(monitor.getMonitorType())
        .setContent(monitor.getContent())
        .setLabelSelector(monitor.getLabelSelector())
        .setMonitorName(monitor.getMonitorName())
        .setResourceId(monitor.getResourceId())
        .setPluginMetadataFields(monitor.getPluginMetadataFields());

    // Monitor Templates do not use policy metadata so this should fail validation
    exceptionRule.expect(ConstraintViolationException.class);
    monitorManagement.updateMonitorTemplate(monitor.getId(), update, true);
    entityManager.flush(); // must flush for entity constraint violations to trigger
  }

  @Test
  public void testPatchMonitorTemplate_success() {
    // make sure the zone we're setting is allowed to be used by this tenant
    List<Zone> zones = List.of(new Zone().setName("public/z-1"),
                               new Zone().setName("public/z-2"));
    when(zoneManagement.getAvailableZonesForTenant(anyString(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{\"type\": \"ping\", \"urls\": [\"localhost\"]}")
            .setMonitorType(MonitorType.ping)
            .setTenantId(POLICY_TENANT)
            .setInterval(Duration.ofSeconds(60))
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND));

    // this has to be populated with all fields otherwise they will be considered as null
    MonitorCU update = new MonitorCU()
        .setZones(List.of("public/z-1", "public/z-2"))
        .setInterval(Duration.ofSeconds(100)) // setting this should trigger a failure on update
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setAgentType(monitor.getAgentType())
        .setMonitorType(monitor.getMonitorType())
        .setContent(monitor.getContent())
        .setLabelSelector(monitor.getLabelSelector())
        .setMonitorName(null)
        .setResourceId(monitor.getResourceId())
        .setPluginMetadataFields(monitor.getPluginMetadataFields());

    final Monitor updatedMonitor = monitorManagement.updateMonitorTemplate(monitor.getId(), update, true);
    entityManager.flush(); // must flush for entity constraint violations to trigger

    // new values
    assertThat(updatedMonitor.getZones(), hasSize(2));
    assertThat(updatedMonitor.getZones(), containsInAnyOrder("public/z-1", "public/z-2"));
    assertThat(updatedMonitor.getInterval(), equalTo(Duration.ofSeconds(100)));
    assertThat(updatedMonitor.getMonitorName(), nullValue());

    // no changes
    assertThat(updatedMonitor.getLabelSelector(), equalTo(monitor.getLabelSelector()));
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(monitor.getLabelSelectorMethod()));
    assertThat(updatedMonitor.getContent(), equalTo(monitor.getContent()));
  }

  @Test
  public void testRemoveMonitorTemplate() {
    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("{}")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)));

    // EXECUTE

    monitorManagement.removeMonitorTemplate(monitor.getId());

    // VERIFY

    final Optional<Monitor> retrieved = monitorManagement.getMonitorTemplate(monitor.getId());
    assertThat(retrieved.isPresent(), equalTo(false));

    verify(monitorPolicyRepository).existsByMonitorTemplateId(monitor.getId());

    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  /**
   * This test simulates the adding of two new monitor policies to a single account
   * that did not have any previously.
   * For this test, the tenant has two resources relevant to both of those policies.
   */
  @Test
  public void testRefreshMonitorUsingTemplatesForTenant_noExistingMonitors() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    // create 2 monitor templates s and configure the policy api/db to use them when queried
    List<UUID> policyMonitorIds = createMonitorsForTenant(2, POLICY_TENANT);
    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
    when(monitorPolicyRepository.findById(any()))
        .thenReturn(Optional.of(new MonitorPolicy().setMonitorTemplateId(policyMonitorIds.get(0))));
    when(monitorPolicyRepository.findById(any()))
        .thenReturn(Optional.of(new MonitorPolicy().setMonitorTemplateId(policyMonitorIds.get(1))));

    // no monitor using templates exists on tenant
    assertThat(monitorRepository.findByTenantIdAndPolicyIdIsNotNull(tenantId), hasSize(0));

    monitorManagement.refreshMonitorUsingTemplatesForTenant(tenantId);

    // monitor using templates now exists on tenant
    assertThat(monitorRepository.findByTenantIdAndPolicyIdIsNotNull(tenantId), hasSize(2));

    // cloning methods were called
    verify(metadataUtils, times(2)).setMetadataFieldsForClonedMonitor(anyString(), any());
    verify(monitorConversionService, times(2)).refreshClonedPlugin(anyString(), any());
  }

  /**
   * This test simulates the refreshing of monitor policies on a single account
   * that already had those policies in place.
   * For this test, the tenant has two resources relevant to both of those policies.
   */
  @Test
  public void testRefreshMonitorUsingTemplatesForTenant_policyAlreadyConfigured() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID policyId = UUID.randomUUID();
    UUID monitorId = currentMonitor.getId();

    // store a monitor for the tenant that is tied to the policy in the event
    createMonitorForPolicyForTenant(tenantId, policyId);

    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(List.of(policyId));
    when(monitorPolicyRepository.findById(any()))
        .thenReturn(Optional.of(new MonitorPolicy().setMonitorTemplateId(monitorId)));

    monitorManagement.refreshMonitorUsingTemplatesForTenant(tenantId);

    // no cloning operations performed
    verifyNoInteractions(boundMonitorRepository, metadataUtils, monitorConversionService);
  }

  /**
   * This test simulates the adding of one new monitor policy to a single account
   * that already had one other policy applied.
   * For this test, the tenant has two resources relevant to both of those policies.
   */
  @Test
  public void testRefreshMonitorUsingTemplatesForTenant_partiallyExistingMonitors() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    // create 2 policies and configure one of them on the customer tenant
    List<UUID> monitorPolicyIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    UUID policyMonitorId = createMonitorsForTenant(1, POLICY_TENANT).get(0);
    createMonitorForPolicyForTenant(tenantId, monitorPolicyIds.get(0));

    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(monitorPolicyIds);
    when(monitorPolicyRepository.findById(any()))
        .thenReturn(Optional.of(new MonitorPolicy().setMonitorTemplateId(policyMonitorId)));

    // no monitor using templates exists on tenant
    assertThat(monitorRepository.findByTenantIdAndPolicyIdIsNotNull(tenantId), hasSize(1));

    monitorManagement.refreshMonitorUsingTemplatesForTenant(tenantId);

    // monitor using templates now exists on tenant
    assertThat(monitorRepository.findByTenantIdAndPolicyIdIsNotNull(tenantId), hasSize(2));

    // cloning methods were called for one monitor
    verify(metadataUtils).setMetadataFieldsForClonedMonitor(anyString(), captorOfMonitor.capture());
    assertThat(captorOfMonitor.getValue().getPolicyId(), equalTo(monitorPolicyIds.get(1)));
    verify(monitorConversionService).refreshClonedPlugin(anyString(), captorOfMonitor.capture());
    assertThat(captorOfMonitor.getValue().getPolicyId(), equalTo(monitorPolicyIds.get(1)));
  }

  /**
   * This test simulates the removing of a monitor policy on a single account
   * that already has two policies in place.
   * For this test, the tenant has two resources relevant to both of those policies.
   */
  @Test
  public void testRefreshMonitorUsingTemplatesForTenant_removePolicy() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID policyId = UUID.randomUUID();

    // store a monitor for the tenant that is tied to the policy in the event
    Monitor clonedMonitor = createMonitorForPolicyForTenant(tenantId, policyId);
    PageRequest pageRequest = PageRequest.of(0,1000);
    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(Collections.emptyList());
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(tenantId, List.of(clonedMonitor.getId()), pageRequest))
        .thenReturn(Page.empty());

    monitorManagement.refreshMonitorUsingTemplatesForTenant(tenantId);

    //monitor using templates no longer exists on tenant
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isEmpty());
  }

  /**
   * Receive a new monitor policy event and process it for a tenant
   * that was not previously using the policy.
   *
   * As cloneMonitor has its own tests this just verifies the basic result.
   */
  @Test
  public void testHandleMonitorPolicyEvent_newPolicyForTenant() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID policyId = UUID.randomUUID();
    UUID policyMonitorId = currentMonitor.getId();

    // handle initial lookup to see if policy exists
    when(monitorPolicyRepository.findById(any()))
        .thenReturn(Optional.of((MonitorPolicy) new MonitorPolicy()
            .setMonitorTemplateId(policyMonitorId)
            .setName(RandomStringUtils.randomAlphabetic(5))
            .setId(policyId)
            .setScope(PolicyScope.GLOBAL)));

    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(List.of(policyId));

    // no monitor using templates exists on tenant
    assertFalse(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isPresent());

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(policyMonitorId)
        .setTenantId(tenantId)
        .setPolicyId(policyId);
    monitorManagement.handleMonitorPolicyEvent(event);

    // monitor using templates now exists on tenant
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isPresent());

    // policy was looked up to see if it exists
    verify(monitorPolicyRepository).findById(policyId);

    // cloning methods were called
    verify(metadataUtils).setMetadataFieldsForClonedMonitor(anyString(), captorOfMonitor.capture());
    assertThat(captorOfMonitor.getValue().getPolicyId(), equalTo(policyId));
    assertThat(captorOfMonitor.getValue().getTenantId(), equalTo(tenantId));

    verify(monitorConversionService).refreshClonedPlugin(anyString(), captorOfMonitor.capture());
    assertThat(captorOfMonitor.getValue().getPolicyId(), equalTo(policyId));
    assertThat(captorOfMonitor.getValue().getTenantId(), equalTo(tenantId));

    verify(boundMonitorRepository).saveAll(anyIterable());
  }

  /**
   * Receive a new monitor policy event and process it for a tenant
   * that was previously using the policy.
   */
  @Test
  public void testHandleMonitorPolicyEvent_existingPolicyForTenant() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID policyId = UUID.randomUUID();
    UUID policyMonitorId = currentMonitor.getId();

    // store a monitor for the tenant that is tied to the policy in the event
    createMonitorForPolicyForTenant(tenantId, policyId);

    when(monitorPolicyRepository.findById(any()))
        .thenReturn(Optional.of((MonitorPolicy) new MonitorPolicy()
            .setMonitorTemplateId(UUID.randomUUID())
            .setName(RandomStringUtils.randomAlphabetic(5))
            .setId(policyId)
            .setScope(PolicyScope.GLOBAL)));

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(policyMonitorId)
        .setTenantId(tenantId)
        .setPolicyId(policyId);
    monitorManagement.handleMonitorPolicyEvent(event);

    verify(monitorPolicyRepository).findById(policyId);

    // no additional actions should occur
    verifyNoInteractions(boundMonitorRepository, metadataUtils, monitorConversionService, policyApi);
  }

  /**
   * Receive a new monitor policy event and process it for a tenant
   * that was not previously using the policy but did have a previous policy in place using
   * the same monitorId.
   *
   * cloneMonitor should not be triggered.
   */
  @Test
  public void testHandleMonitorPolicyEvent_newPolicyForTenant_overridesPrevious() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID oldPolicyId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    UUID policyMonitorId = currentMonitor.getId();

    // store a monitor for the tenant that is tied to the same monitor as the new policy
    createMonitorForPolicyForTenant(tenantId, oldPolicyId);

    // handle initial lookup to see if policy exists
    when(monitorPolicyRepository.findById(policyId))
        .thenReturn(Optional.of((MonitorPolicy) new MonitorPolicy()
            .setMonitorTemplateId(policyMonitorId)
            .setName(RandomStringUtils.randomAlphabetic(5))
            .setId(policyId)
            .setScope(PolicyScope.GLOBAL)));

    // handle lookup of old policy
    when(monitorPolicyRepository.findById(oldPolicyId))
        .thenReturn(Optional.of((MonitorPolicy) new MonitorPolicy()
            .setMonitorTemplateId(policyMonitorId)
            .setName(RandomStringUtils.randomAlphabetic(5))
            .setId(oldPolicyId)
            .setScope(PolicyScope.GLOBAL)));


    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(List.of(policyId));

    // no monitor using templates exists on tenant
    assertFalse(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isPresent());

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(policyMonitorId)
        .setTenantId(tenantId)
        .setPolicyId(policyId);
    monitorManagement.handleMonitorPolicyEvent(event);

    // monitor using templates now exists on tenant
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isPresent());
    // no monitors is tied to the old policy
    assertFalse(monitorRepository.findByTenantIdAndPolicyId(tenantId, oldPolicyId).isPresent());

    // policy was looked up to see if it exists
    verify(monitorPolicyRepository).findById(policyId);

    // old policy was found (and used to locate the relevant existing monitor)
    verify(monitorPolicyRepository).findById(oldPolicyId);

    // no cloning methods were triggered
    verifyNoInteractions(metadataUtils, boundMonitorRepository, monitorConversionService);
  }

  /**
   * Receive a new monitor policy event and process it for a tenant
   * that was previously using the policy.
   *
   * In this case the removed policy was previously overriding another that used the same monitorId.
   * The removal causes the other policy to become "effective" and the existing cloned monitor
   * will be updated to reference that policy instead of the removed one.
   *
   * cloneMonitor should not be triggered.
   */
  @Test
  public void testHandleMonitorPolicyEvent_removePolicyInUse_replacedByOther() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID preExistingPolicyId = UUID.randomUUID();
    UUID policyId = UUID.randomUUID();
    UUID policyMonitorId = currentMonitor.getId();

    // store a monitor for the tenant that is tied to the policy in the event
    Monitor clonedMonitor = createMonitorForPolicyForTenant(tenantId, policyId);

    // handle initial lookup to see if policy exists
    when(monitorPolicyRepository.findById(policyId)).thenReturn(Optional.empty());

    when(policyApi.getEffectiveMonitorPoliciesForTenant(anyString(), anyBoolean()))
        .thenReturn(List.of((MonitorPolicyDTO) new MonitorPolicyDTO()
            .setMonitorId(policyMonitorId)
            .setId(preExistingPolicyId)));

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(policyMonitorId)
        .setTenantId(tenantId)
        .setPolicyId(policyId);
    monitorManagement.handleMonitorPolicyEvent(event);

    // monitor using templates no longer exists on tenant for original policyId
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isEmpty());
    // but does exist for the other policyId and the monitorId is the same as before
    Optional<Monitor> monitor = monitorRepository.findByTenantIdAndPolicyId(tenantId, preExistingPolicyId);
    assertTrue(monitor.isPresent());
    assertThat(monitor.get().getId(), equalTo(clonedMonitor.getId()));

    // verify correct operations were performed
    verify(monitorPolicyRepository).findById(policyId);
    verify(policyApi).getEffectiveMonitorPoliciesForTenant(tenantId, false);

    // no unbind or clone actions should occur
    verifyNoInteractions(boundMonitorRepository, metadataUtils, monitorConversionService);
    verifyNoMoreInteractions(policyApi);
  }

  /**
   * Receive a removal monitor policy event and process it for a tenant
   * that was not previously using the policy.
   */
  @Test
  public void testHandleMonitorPolicyEvent_removePolicyNotInUse() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID policyId = UUID.randomUUID();
    UUID policyMonitorId = currentMonitor.getId();

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(policyMonitorId)
        .setTenantId(tenantId)
        .setPolicyId(policyId);
    monitorManagement.handleMonitorPolicyEvent(event);

    // no additional actions should occur
    verifyNoInteractions(boundMonitorRepository, metadataUtils, monitorConversionService, policyApi);
  }

  /**
   * Receive a new monitor policy event and process it for a tenant
   * that was previously using the policy.
   */
  @Test
  public void testHandleMonitorPolicyEvent_removePolicyInUse() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID policyId = UUID.randomUUID();
    UUID policyMonitorId = currentMonitor.getId();

    // store a monitor for the tenant that is tied to the policy in the event
    Monitor clonedMonitor = createMonitorForPolicyForTenant(tenantId, policyId);
    PageRequest pageRequest = PageRequest.of(0,1000);

    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(Collections.emptyList());
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(tenantId, List.of(clonedMonitor.getId()), pageRequest))
        .thenReturn(Page.empty());

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(policyMonitorId)
        .setTenantId(tenantId)
        .setPolicyId(policyId);
    monitorManagement.handleMonitorPolicyEvent(event);

    // monitor using templates no longer exists on tenant
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isEmpty());
  }

  /**
   * Receive a remove monitor policy event and process it for a tenant
   * that was previously using the policy.
   *
   * Another GLOBAL policy is in place using the same monitorId, so the monitor/bindings should
   * not be used.
   */
  @Test
  public void testHandleMonitorPolicyEvent_removePolicy_butKeepMonitor() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID policyId = UUID.randomUUID();
    UUID policyMonitorId = currentMonitor.getId();

    // store a monitor for the tenant that is tied to the policy in the event
    Monitor clonedMonitor = createMonitorForPolicyForTenant(tenantId, policyId);

    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(Collections.emptyList());
    PageRequest pageRequest = PageRequest.of(0, 1000);

    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(tenantId, List.of(clonedMonitor.getId()), pageRequest))
        .thenReturn(Page.empty());

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(policyMonitorId)
        .setTenantId(tenantId)
        .setPolicyId(policyId);
    monitorManagement.handleMonitorPolicyEvent(event);

    // monitor using templates no longer exists on tenant
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isEmpty());
  }

  /**
   * Process a MonitorPolicyEvent that contains outdated details.
   *
   * Receives a policy event for a policy that has since been updated to be an opt-out policy.
   *
   * The service should ignore the specific values in the kafka event and instead rely
   * on what is stored in the database.
   */
  @Test
  public void testHandleMonitorPolicyEvent_lateProcessing() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID policyId = UUID.randomUUID();
    UUID policyMonitorId = currentMonitor.getId();

    // store a monitor for the tenant that is tied to the policy in the event
    Monitor clonedMonitor = createMonitorForPolicyForTenant(tenantId, policyId);

    List<MonitorPolicyDTO> list = List.of((MonitorPolicyDTO) new MonitorPolicyDTO()
        .setMonitorId(null)
        .setId(policyId));

    // return the opt-out policy from the db
    when(monitorPolicyRepository.findById(any()))
        .thenReturn(Optional.of(new MonitorPolicy().setMonitorTemplateId(null)));

    // this returns empty since we exclude null policies
    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(Collections.emptyList());

    PageRequest pageRequest = PageRequest.of(0, 1000);

    // return the bound monitors associated to the cloned monitor
    BoundMonitor b = new BoundMonitor()
        .setMonitor(clonedMonitor)
        .setResourceId("r-1")
        .setEnvoyId("e-1");
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(anyString(), any(), any()))
        .thenReturn(new PageImpl<>(Collections.singletonList(b)))
        .thenReturn(Page.empty()); // this page is returned after the first is removed

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(policyMonitorId)
        .setTenantId(tenantId)
        .setPolicyId(policyId);
    monitorManagement.handleMonitorPolicyEvent(event);

    // despite the event containing a monitorId, the cloned monitor should be removed
    // due to the policy having a null monitorId in the db
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isEmpty());

    verify(policyApi).getEffectiveMonitorPolicyIdsForTenant(tenantId, false, false);
    verify(monitorPolicyRepository).findById(policyId);
    verify(boundMonitorRepository).deleteAll(Collections.singletonList(b));
    verify(boundMonitorRepository, times(2)).findAllByTenantIdAndMonitor_IdIn(tenantId, List.of(clonedMonitor.getId()), pageRequest);
    verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent().setEnvoyId("e-1"));
    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, policyApi, monitorEventProducer);
  }

  /**
   * Receive a remove monitor policy event for a tenant whose only remaining policy
   * is an opt-out policy.
   *
   * The null monitorId in the opt-out policy should not cause any problems and the newly
   * removed policy should trigger the previously stored monitor to be deleted.
   */
  @Test
  public void testHandleMonitorPolicyEvent_removePolicy_unrelatedOptOutHasNoEffect() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID policyId = UUID.randomUUID();
    UUID policyMonitorId = currentMonitor.getId();
    UUID anotherPolicyId = UUID.randomUUID();

    // store a monitor for the tenant that is tied to the policy in the event
    Monitor clonedMonitor = createMonitorForPolicyForTenant(tenantId, policyId);

    // only return the separate opt-out policy
    // the policy related to the stored monitor has been deleted so will not be returned
    List<MonitorPolicyDTO> list = List.of(
        (MonitorPolicyDTO) new MonitorPolicyDTO()
        .setMonitorId(null)
        .setId(anotherPolicyId));

    when(policyApi.getEffectiveMonitorPoliciesForTenant(anyString(), anyBoolean()))
        .thenReturn(list);
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(anyString(), any(), any()))
        .thenReturn(Page.empty());

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(policyMonitorId)
        .setTenantId(tenantId)
        .setPolicyId(policyId);
    monitorManagement.handleMonitorPolicyEvent(event);

    // no monitors exist for the newly removed policy or the still existing opt-out policy
    assertTrue(monitorRepository.findById(clonedMonitor.getId()).isEmpty());
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, policyId).isEmpty());
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, anotherPolicyId).isEmpty());

    verify(policyApi).getEffectiveMonitorPoliciesForTenant(tenantId, false);
    PageRequest pageRequest = PageRequest.of(0,1000);
    verify(boundMonitorRepository).findAllByTenantIdAndMonitor_IdIn(tenantId, List.of(clonedMonitor.getId()), pageRequest);
    verifyNoMoreInteractions(boundMonitorRepository, policyApi, monitorEventProducer);
  }

  /**
   * Receive an opt-out monitor policy event (i.e. monitorId is `null`)  and process it for a tenant
   * that was previously using the policy.
   *
   * Basically the same as a refreshMonitorUsingTemplatesForTenant test.
   */
  @Test
  public void testHandleMonitorPolicyEvent_optOut() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID originalPolicyId = UUID.randomUUID();

    // store a monitor for the tenant that is tied to the policy in the event
    Monitor clonedMonitor = createMonitorForPolicyForTenant(tenantId, originalPolicyId);
    PageRequest pageRequest = PageRequest.of(0,1000);

    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, originalPolicyId).isPresent());

    when(policyApi.getEffectiveMonitorPolicyIdsForTenant(anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(Collections.emptyList());
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(tenantId, List.of(clonedMonitor.getId()), pageRequest))
        .thenReturn(Page.empty());

    MonitorPolicyEvent event = (MonitorPolicyEvent) new MonitorPolicyEvent()
        .setMonitorId(null)
        .setTenantId(tenantId)
        .setPolicyId(UUID.randomUUID());
    monitorManagement.handleMonitorPolicyEvent(event);

    // monitor using templates no longer exists on tenant
    assertTrue(monitorRepository.findByTenantIdAndPolicyId(tenantId, originalPolicyId).isEmpty());

    verify(policyApi).getEffectiveMonitorPolicyIdsForTenant(tenantId, false, false);
  }

  @Test
  public void testDeleteMonitor_withPolicyId() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID policyId = UUID.randomUUID();
    Monitor monitor = createMonitorForPolicyForTenant(tenantId, policyId);

    assertThatThrownBy(() -> monitorManagement.removeMonitor(tenantId, monitor.getId()))
        .isInstanceOf(DeletionNotAllowedException.class)
        .hasMessageContaining("Cannot remove monitor configured by Policy. Contact your support team to opt out of the policy.");
  }

  private void createMonitors(int count) {
    for (int i = 0; i < count; i++) {
      String tenantId = RandomStringUtils.randomAlphanumeric(10);
      MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
      // limit to local/agent monitors only
      create.setSelectorScope(ConfigSelectorScope.LOCAL);
      create.setZones(Collections.emptyList());
      create.setMonitorType(MonitorType.cpu);
      create.setInterval(Duration.ofSeconds(60));
      monitorManagement.createMonitor(tenantId, create);
    }
  }

  private List<UUID> createMonitorsForTenant(int count, String tenantId) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
      create.setInterval(Duration.ofSeconds(60));
      create.setSelectorScope(ConfigSelectorScope.LOCAL);
      create.setLabelSelectorMethod(LabelSelectorMethod.AND);
      create.setZones(Collections.emptyList());
      create.setMonitorType(MonitorType.cpu);
      Monitor monitor = monitorManagement.createMonitor(tenantId, create);
      ids.add(monitor.getId());
    }
    return ids;
  }

  private Monitor createMonitorForPolicyForTenant(String tenantId, UUID policyId) {
    Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
    monitor.setPolicyId(policyId).setTenantId(tenantId);
    monitor.setInterval(Duration.ofSeconds(60));
    return monitorRepository.save(monitor);
  }
}