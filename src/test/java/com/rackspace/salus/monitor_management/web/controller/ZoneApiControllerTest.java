package com.rackspace.salus.monitor_management.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.web.controller.ZoneApiController;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.services.ZoneManagement;
import com.rackspace.salus.monitor_management.web.model.ZoneCreate;
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

import static org.hamcrest.CoreMatchers.is;
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

    @Test
    public void testGetByZoneName() throws Exception {
        final Zone expectedZone = podamFactory.manufacturePojo(Zone.class);
        when(zoneManagement.getZone(any(), any()))
                .thenReturn(Optional.of(expectedZone));

        mvc.perform(get(
                "/api/tenant/{tenantId}/zones/{name}",
                "t-1", "r-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(expectedZone.toDTO())));
    }

    @Test
    public void testCreateZone() throws Exception {
        Zone zone = podamFactory.manufacturePojo(Zone.class);
        when(zoneManagement.createZone(any(), any()))
                .thenReturn(zone);

        ZoneCreate create = podamFactory.manufacturePojo(ZoneCreate.class);

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
    public void testCreateZoneInvalidName() throws Exception {
        ZoneCreate create = podamFactory.manufacturePojo(ZoneCreate.class);
        create.setName("Cant use non-alphanumeric!!!");

        String errorMsg = "\"name\" Only alphanumeric characters can be used";

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
    public void testDeleteZone() throws Exception {
        mvc.perform(delete(
                "/api/tenant/{tenantId}/zones/{name}",
                "t-1", "r-1"))
                .andDo(print())
                .andExpect(status().isNoContent());
    }
}