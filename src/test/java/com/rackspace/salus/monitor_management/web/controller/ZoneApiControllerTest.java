package com.rackspace.salus.monitor_management.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.errors.ZoneAlreadyExists;
import com.rackspace.salus.monitor_management.errors.ZoneDeletionNotAllowed;
import com.rackspace.salus.monitor_management.services.ZoneManagement;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePrivate;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePublic;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
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
                .setPollerTimeout(random.nextInt(1000) + 30);
    }

    private ZoneCreatePublic newZoneCreatePublic() {
        Random random = new Random();
        return new ZoneCreatePublic()
            .setName(ResolvedZone.PUBLIC_PREFIX + RandomStringUtils.randomAlphanumeric(6))
            .setProvider(RandomStringUtils.randomAlphanumeric(6))
            .setProviderRegion(RandomStringUtils.randomAlphanumeric(6))
            .setPollerTimeout(random.nextInt(1000) + 30)
            .setSourceIpAddresses(podamFactory.manufacturePojo(ArrayList.class, String.class));
    }

    @Test
    public void testGetByZoneName() throws Exception {
        final Zone expectedZone = podamFactory.manufacturePojo(Zone.class);
        when(zoneManagement.getPrivateZone(any(), any()))
                .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
                "/api/tenant/{tenantId}/zones/{name}", "t-1", "z-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(expectedZone.toDTO())));
    }

    @Test
    public void testGetPublicZone() throws Exception {
        final Zone expectedZone = podamFactory.manufacturePojo(Zone.class);
        when(zoneManagement.getPublicZone(any()))
            .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
            "/api/admin/zones/{name}", "z-1")
            .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(objectMapper.writeValueAsString(expectedZone.toDTO())));
    }

    @Test
    public void testCreatePrivateZone() throws Exception {
        Zone zone = podamFactory.manufacturePojo(Zone.class);
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
                .andExpect(content().json(objectMapper.writeValueAsString(zone.toDTO())));
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
        Zone zone = podamFactory.manufacturePojo(Zone.class);
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
                .andExpect(content().json(objectMapper.writeValueAsString(zone.toDTO())));
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
        Zone zone = podamFactory.manufacturePojo(Zone.class);
        when(zoneManagement.createPublicZone(any()))
            .thenReturn(zone);

        ZoneCreatePublic create = newZoneCreatePublic();
        create.setSourceIpAddresses(Collections.singletonList("50.57.61.0/26"));

        mvc.perform(post(
            "/api/admin/zones")
            .content(objectMapper.writeValueAsString(create))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()))
            .andExpect(status().isCreated())
            .andExpect(content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(objectMapper.writeValueAsString(zone.toDTO())));
    }

    @Test
    public void testCreatePublicZoneInvalidName() throws Exception {
        ZoneCreatePublic create = newZoneCreatePublic();
        create.setName("Cant use non-alphanumeric!!!");
        create.setSourceIpAddresses(Collections.singletonList("50.57.61.0/26"));

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
}