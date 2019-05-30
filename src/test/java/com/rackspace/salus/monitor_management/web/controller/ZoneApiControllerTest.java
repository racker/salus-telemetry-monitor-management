package com.rackspace.salus.monitor_management.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.errors.ZoneAlreadyExists;
import com.rackspace.salus.monitor_management.errors.ZoneDeletionNotAllowed;
import com.rackspace.salus.monitor_management.services.ZoneManagement;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePrivate;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePublic;
import com.rackspace.salus.monitor_management.web.model.ZoneState;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileCopyUtils;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@WebMvcTest(ZoneApiController.class)
public class ZoneApiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ZoneManagement zoneManagement;

    @Autowired
    private ObjectMapper objectMapper;


    private PodamFactory podamFactory = new PodamFactoryImpl();

    private ZoneCreatePrivate newZoneCreatePrivate() {
        Random random = new Random();
        return new ZoneCreatePrivate()
                .setName(RandomStringUtils.randomAlphanumeric(10))
                .setPollerTimeout(random.nextInt(1000) + 30L);
    }

    private ZoneCreatePublic newZoneCreatePublic() {
        Random random = new Random();
        return new ZoneCreatePublic()
            .setName(ResolvedZone.PUBLIC_PREFIX + RandomStringUtils.randomAlphanumeric(6))
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
            .setSourceIpAddresses(Collections.emptyList());

        when(zoneManagement.getPrivateZone(any(), any()))
                .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
                "/api/tenant/{tenantId}/zones/{name}", "t-1", "z-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                    readContent("ZoneApiControllerTest/privateZone_basic.json"), true));
    }

    @Test
    public void testGetAvailablePublicZoneForTenant() throws Exception {
        final Zone expectedZone = new Zone()
            .setId(UUID.randomUUID())
            .setName("public/zone-1")
            .setPollerTimeout(Duration.ofSeconds(60))
            .setProvider("p-1")
            .setProviderRegion("p-r-1")
            .setPublic(true)
            .setState(ZoneState.ACTIVE)
            .setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"));

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

    @Test
    public void testGetAvailablePublicZoneAsAdmin() throws Exception {
        final Zone expectedZone = podamFactory.manufacturePojo(Zone.class);
        when(zoneManagement.getPublicZone(any()))
            .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
            "/api/admin/zones/{name}", "public/z-1")
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(objectMapper.writeValueAsString(expectedZone.toDTO()), true));
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
            .setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"));

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
            .setSourceIpAddresses(Collections.emptyList());

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
        String error = "Zone already exists with name z-1 on tenant t-1";
        when(zoneManagement.createPrivateZone(any(), any()))
            .thenThrow(new ZoneAlreadyExists(error));

        ZoneCreatePrivate create = newZoneCreatePrivate();

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
            .setSourceIpAddresses(Collections.emptyList());

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

        String errorMsg = "\"name\" Only alphanumeric and underscore characters can be used";

        mvc.perform(post(
                "/api/tenant/{tenantId}/zones", "t-1")
                .content(objectMapper.writeValueAsString(create))
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is(errorMsg)));
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
            .setSourceIpAddresses(Collections.singletonList("127.0.0.1/27"));

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
                readContent("ZoneApiControllerTest/publicZone_basic.json"), true));
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

        String errorMsg = "\"sourceIpAddresses\" All values must be valid CIDR notation";

        mvc.perform(post(
            "/api/admin/zones")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isBadRequest())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message", is(errorMsg)));
    }

    @Test
    public void testCreatePublicZoneEmptyIpList() throws Exception {
        ZoneCreatePublic create = newZoneCreatePublic();
        create.setSourceIpAddresses(Collections.emptyList());

        String errorMsg = "\"sourceIpAddresses\" must not be empty";

        mvc.perform(post(
            "/api/admin/zones")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isBadRequest())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message", is(errorMsg)));
    }

    @Test
    public void testDeletePrivateZone() throws Exception {
        mvc.perform(delete(
                "/api/tenant/{tenantId}/zones/{name}",
                "t-1", "z-1"))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    public void testDeletePrivateZoneWithMonitors() throws Exception {
        String error = "Cannot remove zone with configured monitors. Found 2.";
        doThrow(new ZoneDeletionNotAllowed(error))
            .when(zoneManagement).removePrivateZone(any(), any());

        ZoneCreatePrivate create = newZoneCreatePrivate();

        mvc.perform(delete(
            "/api/tenant/{tenantId}/zones/{name}", "t-1", "z-1")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isConflict());
    }

    private static String readContent(String resource) throws IOException {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return FileCopyUtils.copyToString(new InputStreamReader(in));
        }
    }
}