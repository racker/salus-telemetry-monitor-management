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

package com.rackspace.salus.monitor_management.web.controller;

import static com.rackspace.salus.common.util.SpringResourceUtils.readContent;
import static com.rackspace.salus.test.WebTestUtils.validationError;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.errors.DeletionNotAllowedException;
import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.services.ZoneManagement;
import com.rackspace.salus.monitor_management.web.model.ZoneAssignmentCount;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePrivate;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePublic;
import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import com.rackspace.salus.telemetry.entities.Zone;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.model.ZoneState;
import com.rackspace.salus.telemetry.repositories.TenantMetadataRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;


@RunWith(SpringRunner.class)
@WebMvcTest(ZoneApiController.class)
@ActiveProfiles("test")
@Import({SimpleMeterRegistry.class})
public class ZoneApiControllerTest {

    // A timestamp to be used in tests that translates to "1970-01-02T03:46:40Z"
    private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochSecond(100000);

    @Autowired
    MockMvc mvc;

    @MockBean
    ZoneManagement zoneManagement;

    @MockBean
    MonitorManagement monitorManagement;

    @MockBean
    TenantMetadataRepository tenantMetadataRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    ZoneStorage zoneStorage;

    private PodamFactory podamFactory = new PodamFactoryImpl();

    private ZoneCreatePrivate newZoneCreatePrivate() {
        Random random = new Random();
        return new ZoneCreatePrivate()
                .setName(RandomStringUtils.randomAlphanumeric(10).toLowerCase())
                .setPollerTimeout(random.nextInt(1000) + 30L);
    }

    private ZoneCreatePublic newZoneCreatePublic() {
        Random random = new Random();
        return new ZoneCreatePublic()
            .setName(ResolvedZone.PUBLIC_PREFIX + RandomStringUtils.randomAlphanumeric(6).toLowerCase())
            .setProvider(RandomStringUtils.randomAlphanumeric(6))
            .setProviderRegion(RandomStringUtils.randomAlphanumeric(6))
            .setPollerTimeout(random.nextInt(1000) + 30L)
            .setSourceIpAddresses(podamFactory.manufacturePojo(ArrayList.class, String.class));
    }

    @Test
    public void testGetAvailablePrivateZoneForTenant() throws Exception {
        final Zone expectedZone = new Zone()
            .setId(UUID.randomUUID())
            .setName("testPrivateZone")
            .setPollerTimeout(Duration.ofSeconds(60))
            .setProvider("p-1")
            .setProviderRegion("p-r-1")
            .setPublic(false)
            .setState(ZoneState.ACTIVE)
            .setSourceIpAddresses(Collections.emptyList())
            .setCreatedTimestamp(DEFAULT_TIMESTAMP)
            .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

        when(zoneManagement.getPrivateZone(any(), any()))
                .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
            "/api/tenant/{tenantId}/zones/{name}",
            "t-1", RandomStringUtils.randomAlphanumeric(10))
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                readContent("ZoneApiControllerTest/privateZone_basic.json"), true));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    public void testGetAvailablePublicZoneForTenantAsCustomer() throws Exception {
        final Zone expectedZone = new Zone()
            .setId(UUID.randomUUID())
            .setName("public/zone-1")
            .setPollerTimeout(Duration.ofSeconds(60))
            .setProvider("p-1")
            .setProviderRegion("p-r-1")
            .setPublic(true)
            .setState(ZoneState.ACTIVE)
            .setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"))
            .setCreatedTimestamp(DEFAULT_TIMESTAMP)
            .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

        when(zoneManagement.getPublicZone(any()))
            .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
            "/api/tenant/{tenantId}/zones/{name}", "t-1", "public/zone-1")
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                // the STATE field should not be included
                readContent("ZoneApiControllerTest/publicZone_as_customer.json"), true));
    }

    @WithMockUser(roles = "EMPLOYEE")
    @Test
    public void testGetAvailablePublicZoneForTenantAsEmployee() throws Exception {
        final Zone expectedZone = new Zone()
            .setId(UUID.randomUUID())
            .setName("public/zone-1")
            .setPollerTimeout(Duration.ofSeconds(60))
            .setProvider("p-1")
            .setProviderRegion("p-r-1")
            .setPublic(true)
            .setState(ZoneState.ACTIVE)
            .setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"))
            .setCreatedTimestamp(DEFAULT_TIMESTAMP)
            .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

        when(zoneManagement.getPublicZone(any()))
            .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
            "/api/tenant/{tenantId}/zones/{name}", "t-1", "public/zone-1")
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                // the STATE field should not be included
                readContent("ZoneApiControllerTest/publicZone_as_admin.json"), true));
    }

    @WithMockUser(roles = "ENGINEER")
    @Test
    public void testGetAvailablePublicZoneForTenantAsAdmin() throws Exception {
        final Zone expectedZone = new Zone()
            .setId(UUID.randomUUID())
            .setName("public/zone-1")
            .setPollerTimeout(Duration.ofSeconds(60))
            .setProvider("p-1")
            .setProviderRegion("p-r-1")
            .setPublic(true)
            .setState(ZoneState.ACTIVE)
            .setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"))
            .setCreatedTimestamp(DEFAULT_TIMESTAMP)
            .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

        when(zoneManagement.getPublicZone(any()))
            .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
            "/api/tenant/{tenantId}/zones/{name}", "t-1", "public/zone-1")
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                // the STATE field should not be included
                readContent("ZoneApiControllerTest/publicZone_as_admin.json"), true));
    }

    @Test
    public void testGetAvailablePublicZoneAdminApi() throws Exception {
        final Zone expectedZone = podamFactory.manufacturePojo(Zone.class);
        when(zoneManagement.getPublicZone(any()))
            .thenReturn(Optional.of(expectedZone));

        final String zoneName = "public/" + RandomStringUtils.randomAlphabetic(6);
        mvc.perform(get(
            "/api/admin/zones/{name}", zoneName)
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(objectMapper.writeValueAsString(new ZoneDTO(expectedZone)), true));
    }

    @Test
    public void testGetInvalidPublicZone() throws Exception {
        final String errorMsg = "No zone found named testPublicZone";

        mvc.perform(get(
            "/api/admin/zones/{name}", "testPublicZone")
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", is(errorMsg)));
    }

    @Test
    public void testGetInvalidPrivateZone() throws Exception {
        final String errorMsg = "No zone found named public/testPrivateZone";

        mvc.perform(get(
            "/api/admin/zones/{name}", "public/testPrivateZone")
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", is(errorMsg)));
    }

    @Test
    public void testGetPublicZone() throws Exception {
        final Zone expectedZone = new Zone()
            .setId(UUID.randomUUID())
            .setName("public/testPublicZone")
            .setPollerTimeout(Duration.ofSeconds(60))
            .setProvider("p-1")
            .setProviderRegion("p-r-1")
            .setPublic(true)
            .setState(ZoneState.INACTIVE)
            .setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"))
            .setCreatedTimestamp(DEFAULT_TIMESTAMP)
            .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

        when(zoneManagement.getPublicZone(any()))
            .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
            "/api/admin/zones/{name}", "public/testPublicZone")
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                readContent("ZoneApiControllerTest/publicZone_basic.json"), true));
    }

    @Test
    public void testCreatePrivateZone() throws Exception {
        final Zone zone = new Zone()
            .setId(UUID.randomUUID())
            .setName("testPrivateZone")
            .setPollerTimeout(Duration.ofSeconds(60))
            .setProvider("p-1")
            .setProviderRegion("p-r-1")
            .setPublic(false)
            .setState(ZoneState.ACTIVE)
            .setSourceIpAddresses(Collections.emptyList())
            .setCreatedTimestamp(DEFAULT_TIMESTAMP)
            .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

        when(zoneManagement.createPrivateZone(any(), any()))
                .thenReturn(zone);

        ZoneCreatePrivate create = newZoneCreatePrivate();

        mvc.perform(post(
                    "/api/tenant/{tenantId}/zones", "t-1")
                .content(objectMapper.writeValueAsString(create))
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isCreated())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                readContent("ZoneApiControllerTest/privateZone_basic.json"), true));
    }

    @Test
    public void testCreateDuplicatePrivateZone() throws Exception {
        ZoneCreatePrivate create = newZoneCreatePrivate();
        String error = String.format(
            "Zone already exists with name %s on tenant t-1", create.getName());

        when(zoneManagement.createPrivateZone(any(), any()))
            .thenThrow(new AlreadyExistsException(error));


        mvc.perform(post(
            "/api/tenant/{tenantId}/zones", "t-1")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void testCreatePrivateZoneWithUnderscores() throws Exception {
        final Zone zone = new Zone()
            .setId(UUID.randomUUID())
            .setName("testPrivateZone_with_underscores")
            .setPollerTimeout(Duration.ofSeconds(60))
            .setProvider("p-1")
            .setProviderRegion("p-r-1")
            .setPublic(false)
            .setState(ZoneState.ACTIVE)
            .setSourceIpAddresses(Collections.emptyList())
            .setCreatedTimestamp(DEFAULT_TIMESTAMP)
            .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

        when(zoneManagement.createPrivateZone(any(), any()))
                .thenReturn(zone);

        ZoneCreatePrivate create = newZoneCreatePrivate();
        create.setName("underscores_are_allowed");

        mvc.perform(post(
                "/api/tenant/{tenantId}/zones", "t-1")
                .content(objectMapper.writeValueAsString(create))
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isCreated())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                    readContent("ZoneApiControllerTest/privateZone_underscores.json"), true));
    }

    @Test
    public void testCreatePrivateZoneInvalidName() throws Exception {
        ZoneCreatePrivate create = newZoneCreatePrivate();
        create.setName("Cant use non-alphanumeric!!!");

        mvc.perform(post(
            "/api/tenant/{tenantId}/zones", "t-1")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isBadRequest())
            .andExpect(validationError("name",
                "Only lowercase alphanumeric and underscore characters can be used"));
    }

    @Test
    public void testCreatePublicZone() throws Exception {
        final Zone zone = new Zone()
            .setId(UUID.randomUUID())
            .setName("public/testPublicZone")
            .setPollerTimeout(Duration.ofSeconds(60))
            .setProvider("p-1")
            .setProviderRegion("p-r-1")
            .setPublic(true)
            .setState(ZoneState.INACTIVE)
            .setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"))
            .setCreatedTimestamp(DEFAULT_TIMESTAMP)
            .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

        when(zoneManagement.createPublicZone(any()))
            .thenReturn(zone);

        ZoneCreatePublic create = newZoneCreatePublic();
        create.setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"));

        mvc.perform(post(
            "/api/admin/zones")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isCreated())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                    readContent("ZoneApiControllerTest/publicZone_basic.json")));
    }

    @Test
    public void testCreatePublicZoneInvalidName() throws Exception {
        ZoneCreatePublic create = newZoneCreatePublic();
        create.setName("Cant use non-alphanumeric!!!");
        create.setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"));

        String errorMsg = "\"name\" Only alphanumeric, underscores, and slashes can be used";

        mvc.perform(post(
            "/api/admin/zones")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreatePublicZoneInvalidSourceIps() throws Exception {
        ZoneCreatePublic create = newZoneCreatePublic();
        create.setSourceIpAddresses(Collections.singletonList("a.b.c.d"));

        mvc.perform(post(
            "/api/admin/zones")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isBadRequest())
            .andExpect(validationError("sourceIpAddresses",
                "All values must be valid CIDR notation"));
    }

    @Test
    public void testCreatePublicZoneEmptyIpList() throws Exception {
        ZoneCreatePublic create = newZoneCreatePublic();
        create.setSourceIpAddresses(Collections.emptyList());

        mvc.perform(post(
            "/api/admin/zones")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isBadRequest())
            .andExpect(validationError("sourceIpAddresses",
                "must not be empty"));
    }

    @Test
    public void testDeletePrivateZone() throws Exception {
        mvc.perform(delete(
                "/api/tenant/{tenantId}/zones/{name}",
                "t-1", RandomStringUtils.randomAlphanumeric(10)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    public void testDeletePrivateZoneWithMonitors() throws Exception {
        String error = "Cannot remove zone with configured monitors. Found 2.";
        doThrow(new DeletionNotAllowedException(error))
            .when(zoneManagement).removePrivateZone(any(), any());

        ZoneCreatePrivate create = newZoneCreatePrivate();

        mvc.perform(delete(
            "/api/tenant/{tenantId}/zones/{name}",
            "t-1", RandomStringUtils.randomAlphanumeric(10))
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isConflict());
    }

    @Test
    public void testGetPrivateZoneAssignmentCounts_valid() throws Exception {
        when(zoneManagement.exists(any(), any()))
            .thenReturn(true);

        List<ZoneAssignmentCount> expected = List.of(
            new ZoneAssignmentCount().setResourceId("r-1").setEnvoyId("e-1").setAssignments(3).setConnected(true),
            new ZoneAssignmentCount().setResourceId("r-2").setEnvoyId("e-2").setAssignments(1).setConnected(false)
        );

        when(monitorManagement.getZoneAssignmentCounts(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(expected));

        final String zoneName = RandomStringUtils.randomAlphanumeric(10);
        final MvcResult result = mvc.perform(
            get("/api/tenant/{tenantId}/zone-assignment-counts/{name}",
                "t-1", zoneName))
            // CompletableFuture return value, so the request is asynchronous
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                readContent("ZoneApiControllerTest/privateZoneAssignmentCounts_valid.json"),
                true));

        verify(zoneManagement).exists("t-1", zoneName);
        verify(monitorManagement).getZoneAssignmentCounts("t-1", zoneName);

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testGetPrivateZoneAssignmentCounts_missingZone() throws Exception {
        when(zoneManagement.exists(any(), any()))
            .thenReturn(false);

        mvc.perform(
            get("/api/tenant/{tenantId}/zone-assignment-counts/{name}",
                "t-1", "doesNotExist"))
            .andExpect(status().isNotFound());

        verify(zoneManagement).exists("t-1", "doesNotExist");

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testRebalancePrivateZone_valid() throws Exception {
        when(zoneManagement.exists(any(), any()))
            .thenReturn(true);

        when(monitorManagement.rebalanceZone(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(3));

        final String zoneName = RandomStringUtils.randomAlphabetic(10);
        final MvcResult result = mvc.perform(
            post("/api/tenant/{tenantId}/rebalance-zone/{name}",
                "t-1", zoneName
            )
        )
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.reassigned", equalTo(3)));

        verify(zoneManagement).exists("t-1", zoneName);
        verify(monitorManagement).rebalanceZone("t-1", zoneName);

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testRebalancePrivateZone_missingZone() throws Exception {
        when(zoneManagement.exists(any(), any()))
            .thenReturn(false);

        final String zoneName = RandomStringUtils.randomAlphabetic(10);
        mvc.perform(
            post("/api/tenant/{tenantId}/rebalance-zone/{name}",
                "t-1", zoneName
            )
        )
            .andExpect(status().isNotFound());

        verify(zoneManagement).exists("t-1", zoneName);

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testGetPublicZoneAssignmentCounts_valid() throws Exception {
        when(zoneManagement.publicZoneExists(any()))
            .thenReturn(true);

        List<ZoneAssignmentCount> expected = List.of(
            new ZoneAssignmentCount().setResourceId("r-1").setEnvoyId("e-1").setAssignments(3).setConnected(true),
            new ZoneAssignmentCount().setResourceId("r-2").setEnvoyId("e-2").setAssignments(1).setConnected(false)
        );

        when(monitorManagement.getZoneAssignmentCounts(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(expected));

        final MvcResult result = mvc.perform(
            get("/api/admin/zone-assignment-counts/{name}",
                "public/west"))
            // CompletableFuture return value, so the request is asynchronous
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                readContent("ZoneApiControllerTest/privateZoneAssignmentCounts_valid.json"),
                true));

        verify(zoneManagement).publicZoneExists("public/west");
        verify(monitorManagement).getZoneAssignmentCounts(null, "public/west");

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testGetPublicZoneAssignmentCounts_missingZone() throws Exception {
        when(zoneManagement.publicZoneExists(any()))
            .thenReturn(false);

        mvc.perform(
            get("/api/admin/zone-assignment-counts/{name}",
                "public/doesNotExist"))
            .andExpect(status().isNotFound());

        verify(zoneManagement).publicZoneExists("public/doesNotExist");

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testGetPublicZoneAssignmentCounts_notPublic() throws Exception {
        when(zoneManagement.publicZoneExists(any()))
            .thenReturn(false);

        mvc.perform(
            get("/api/admin/zone-assignment-counts/{name}",
                "privateZone"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", equalTo("Must provide a public zone name")));

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testRebalancePublicZone_valid() throws Exception {
        when(zoneManagement.publicZoneExists(any()))
            .thenReturn(true);

        when(monitorManagement.rebalanceZone(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(3));

        final MvcResult result = mvc.perform(
            post("/api/admin/rebalance-zone/{name}",
                "public/west"
            )
        )
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.reassigned", equalTo(3)));

        verify(zoneManagement).publicZoneExists("public/west");
        verify(monitorManagement).rebalanceZone(null, "public/west");

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testRebalancePublicZone_missingZone() throws Exception {
        when(zoneManagement.publicZoneExists(any()))
            .thenReturn(false);

        mvc.perform(
            post("/api/admin/rebalance-zone/{name}",
                "public/west"
            )
        )
            .andExpect(status().isNotFound());

        verify(zoneManagement).publicZoneExists("public/west");

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testRebalancePublicZone_notPublic() throws Exception {
        when(zoneManagement.publicZoneExists(any()))
            .thenReturn(false);

        mvc.perform(
            post("/api/admin/rebalance-zone/{name}",
                "notPublic"
            )
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", equalTo("Must provide a public zone name")));

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void testCreatePublicZoneInvalidPublicZoneName() throws Exception {
        ZoneCreatePublic create = newZoneCreatePublic();
        create.setName("invalid/zone");
        create.setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"));

        mvc.perform(post(
            "/api/admin/zones")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message",
                equalTo("One or more field validations failed: name")));
    }

    @Test
    public void getPrivateZoneAssignmentCountsPerTenant() throws Exception {

        Map<String, List<ZoneAssignmentCount>> expected = Map.of("z-1", Collections.singletonList(
            new ZoneAssignmentCount().setResourceId("r-1").setEnvoyId("e-1").setAssignments(3)
                .setConnected(true)),
            "z-2", Collections.singletonList(
                new ZoneAssignmentCount().setResourceId("r-2").setEnvoyId("e-2").setAssignments(1)
                    .setConnected(false)),
            "z-3", Collections.emptyList());

        when(monitorManagement.getZoneAssignmentCountForTenant(any()))
            .thenReturn(CompletableFuture.completedFuture(expected));

        final String zoneName = RandomStringUtils.randomAlphanumeric(10);
        final MvcResult result = mvc.perform(
            get("/api/tenant/{tenantId}/zone-assignment-counts", "t-1"))
            // CompletableFuture return value, so the request is asynchronous
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                readContent("ZoneApiControllerTest/privateZoneAssignmentCountPerTenant_valid.json"),
                true));

        verify(monitorManagement).getZoneAssignmentCountForTenant("t-1");

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }

    @Test
    public void getPublicZoneAssignmentCountsPerTenant() throws Exception {

        Map<String, List<ZoneAssignmentCount>> expected = Map.of("public/west", Collections.singletonList(
            new ZoneAssignmentCount().setResourceId("r-1").setEnvoyId("e-1").setAssignments(3)
                .setConnected(true)),
            "public/east", Collections.singletonList(
                new ZoneAssignmentCount().setResourceId("r-2").setEnvoyId("e-2").setAssignments(1)
                    .setConnected(false)),
            "public/north", Collections.emptyList());

        when(monitorManagement.getZoneAssignmentCountForTenant(any()))
            .thenReturn(CompletableFuture.completedFuture(expected));

        final MvcResult result = mvc.perform(
            get("/api/admin/zone-assignment-counts"))
            // CompletableFuture return value, so the request is asynchronous
            .andExpect(request().asyncStarted())
            .andReturn();

        mvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                readContent("ZoneApiControllerTest/publicZoneAssignmentCountPerTenant_valid.json"),
                true));

        verify(monitorManagement).getZoneAssignmentCountForTenant(ResolvedZone.PUBLIC);

        verifyNoMoreInteractions(zoneManagement, monitorManagement);
    }
}