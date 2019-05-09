package com.rackspace.salus.monitor_management.services;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.repositories.ZoneRepository;
import com.rackspace.salus.monitor_management.web.model.ZoneCreate;
import com.rackspace.salus.monitor_management.web.model.ZoneUpdate;
import com.rackspace.salus.telemetry.etcd.services.AgentsCatalogService;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;

@RunWith(SpringRunner.class)
@DataJpaTest(showSql = false)
@Import({ZoneManagement.class, ObjectMapper.class})
public class ZoneManagementTest {

    @MockBean
    MonitorManagement monitorManagement;

    @MockBean
    ZoneStorage zoneStorage;

    @MockBean
    AgentsCatalogService agentsCatalogService;

    @Autowired
    ZoneRepository zoneRepository;

    @Autowired
    ZoneManagement zoneManagement;

    private final String DEFAULT_ZONE = "public/default";

    private PodamFactory podamFactory = new PodamFactoryImpl();

    @Autowired
    ObjectMapper objectMapper;

    @Before
    public void setUp() {
        // create a default public zone
        Zone zone = new Zone()
                .setTenantId(ZoneManagement.PUBLIC)
                .setName(DEFAULT_ZONE)
                .setEnvoyTimeout(Duration.ofSeconds(100));
        zoneRepository.save(zone);
    }

    private void createZonesForTenant(int count, String tenantId) {
        for (int i = 0; i < count; i++) {
            ZoneCreate create = podamFactory.manufacturePojo(ZoneCreate.class);
            create.setName(RandomStringUtils.randomAlphanumeric(10));
            zoneManagement.createZone(tenantId, create);
        }
    }

    @Test
    public void testGetZone() {
        Optional<Zone> zone = zoneManagement.getZone(ZoneManagement.PUBLIC, DEFAULT_ZONE);
        assertTrue(zone.isPresent());
        assertThat(zone.get().getId(), notNullValue());
        assertThat(zone.get().getTenantId(), equalTo(ZoneManagement.PUBLIC));
        assertThat(zone.get().getName(), equalTo(DEFAULT_ZONE));
    }

    @Test
    public void testCreateZone() {
        ZoneCreate create = podamFactory.manufacturePojo(ZoneCreate.class);
        create.setName(RandomStringUtils.randomAlphanumeric(10));
        String tenant = RandomStringUtils.randomAlphabetic(10);
        Zone zone = zoneManagement.createZone(tenant, create);
        assertThat(zone.getId(), notNullValue());
        assertThat(zone.getTenantId(), equalTo(tenant));
        assertThat(zone.getName(), equalTo(create.getName()));
        assertThat(zone.getEnvoyTimeout(), equalTo(Duration.ofSeconds(create.getPollerTimeout())));

        Optional<Zone> z = zoneManagement.getZone(tenant, create.getName());
        assertTrue(z.isPresent());
        assertThat(z.get().getName(), equalTo(create.getName()));
    }

    @Test
    public void testCreateZoneNonAlphanumericName() {
        // This should be successful because the alphanumeric validation only happens via Spring MVC.
        ZoneCreate create = podamFactory.manufacturePojo(ZoneCreate.class);
        create.setName("onlyAlphaNumericAreAllowed!!!");
        String tenant = RandomStringUtils.randomAlphabetic(10);
        Zone zone = zoneManagement.createZone(tenant, create);
        assertThat(zone.getId(), notNullValue());
        assertThat(zone.getTenantId(), equalTo(tenant));
        assertThat(zone.getName(), equalTo(create.getName()));
        assertThat(zone.getEnvoyTimeout(), equalTo(Duration.ofSeconds(create.getPollerTimeout())));

        Optional<Zone> z = zoneManagement.getZone(tenant, create.getName());
        assertTrue(z.isPresent());
        assertThat(z.get().getName(), equalTo(create.getName()));
    }

    @Test
    public void testUpdateZone() {
        Zone original = zoneManagement.getAvailableZonesForTenant(ZoneManagement.PUBLIC).get(0);

        assertThat(original, notNullValue());

        ZoneUpdate update = new ZoneUpdate().setPollerTimeout(original.getEnvoyTimeout().getSeconds() + 100);

        Zone zone = zoneManagement.updateZone(ZoneManagement.PUBLIC, original.getName(), update);
        assertThat(zone.getId(), equalTo(original.getId()));
        assertThat(zone.getTenantId(), equalTo(original.getTenantId()));
        assertThat(zone.getName(), equalTo(original.getName()));
        assertThat(zone.getEnvoyTimeout(), equalTo(Duration.ofSeconds(update.getPollerTimeout())));

        Optional<Zone> z = zoneManagement.getZone(ZoneManagement.PUBLIC, zone.getName());
        assertTrue(z.isPresent());
        assertThat(z.get().getEnvoyTimeout().getSeconds(), equalTo(update.getPollerTimeout()));
    }

    @Test
    public void testGetAvailableZonesForTenant() {
        Random random = new Random();
        int privateCount = random.nextInt(20);
        int publicCount = random.nextInt(5);
        String tenant = RandomStringUtils.randomAlphabetic(10);
        String unrelatedTenant = RandomStringUtils.randomAlphabetic(10);

        // there is one default zone in these tests
        assertThat(zoneManagement.getAvailableZonesForTenant(tenant), hasSize(1));

        // any new private zone for the tenant should be visible
        createZonesForTenant(privateCount, tenant);
        assertThat(zoneManagement.getAvailableZonesForTenant(tenant), hasSize(1 + privateCount));

        // new public zones should be visible too
        createZonesForTenant(publicCount, ZoneManagement.PUBLIC);
        assertThat(zoneManagement.getAvailableZonesForTenant(tenant), hasSize(1 + privateCount + publicCount));

        // Another tenant can only see public zones
        assertThat(zoneManagement.getAvailableZonesForTenant(unrelatedTenant), hasSize(1 + publicCount));
    }
}