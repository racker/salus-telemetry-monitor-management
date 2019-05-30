package com.rackspace.salus.monitor_management.services;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.errors.ZoneDeletionNotAllowed;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.repositories.ZoneRepository;
import com.rackspace.salus.monitor_management.web.model.ZoneState;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePrivate;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePublic;
import com.rackspace.salus.monitor_management.web.model.ZoneUpdate;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
import java.util.Collections;
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

    @Autowired
    ZoneRepository zoneRepository;

    @Autowired
    MonitorRepository monitorRepository;

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
                .setTenantId(ResolvedZone.PUBLIC)
                .setName(DEFAULT_ZONE)
                .setPollerTimeout(Duration.ofSeconds(100));
        zoneRepository.save(zone);
    }

    private Zone createPublicZone() {
        ZoneCreatePublic create = podamFactory.manufacturePojo(ZoneCreatePublic.class);
        create.setName(ResolvedZone.PUBLIC_PREFIX + RandomStringUtils.randomAlphanumeric(6));
        return zoneManagement.createPublicZone(create);
    }

    private List<Zone> createPublicZones(int count) {
        List<Zone> zones = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            zones.add(createPublicZone());
        }
        return zones;
    }

    private Zone createPrivateZoneForTenant(String tenantId) {
        ZoneCreatePrivate create = podamFactory.manufacturePojo(ZoneCreatePrivate.class);
        create.setName(RandomStringUtils.randomAlphanumeric(10));
        return zoneManagement.createPrivateZone(tenantId, create);
    }

    private List<Zone> createPrivateZonesForTenant(int count, String tenantId) {
        List<Zone> zones = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            zones.add(createPrivateZoneForTenant(tenantId));
        }
        return zones;
    }

    private void createRemoteMonitorsForTenant(int count, String tenantId, String zone) {
        for (int i = 0; i < count; i++) {
            Monitor create = podamFactory.manufacturePojo(Monitor.class);
            create.setSelectorScope(ConfigSelectorScope.REMOTE);
            create.setZones(Collections.singletonList(zone));
            create.setTenantId(tenantId);
            monitorRepository.save(create);
        }
    }

    @Test
    public void testGetPublicZone() {
        Optional<Zone> zone = zoneManagement.getPublicZone(DEFAULT_ZONE);
        assertTrue(zone.isPresent());
        assertThat(zone.get().getId(), notNullValue());
        assertThat(zone.get().getTenantId(), equalTo(ResolvedZone.PUBLIC));
        assertThat(zone.get().getName(), equalTo(DEFAULT_ZONE));
    }

    @Test
    public void testGetPrivateZone() {
        String tenant = RandomStringUtils.randomAlphabetic(10);
        String privateZone = RandomStringUtils.randomAlphabetic(10);

        Zone created = new Zone()
            .setTenantId(tenant)
            .setName(privateZone)
            .setPollerTimeout(Duration.ofSeconds(100));
        zoneRepository.save(created);

        Optional<Zone> zone = zoneManagement.getPrivateZone(tenant, privateZone);
        assertTrue(zone.isPresent());
        assertThat(zone.get().getId(), notNullValue());
        assertThat(zone.get().getTenantId(), equalTo(tenant));
        assertThat(zone.get().getName(), equalTo(privateZone));
    }

    @Test
    public void testCreatePrivateZone() {
        ZoneCreatePrivate create = new ZoneCreatePrivate()
            .setName(RandomStringUtils.randomAlphanumeric(6))
            .setProvider(RandomStringUtils.randomAlphanumeric(6))
            .setProviderRegion(RandomStringUtils.randomAlphanumeric(6))
            .setPollerTimeout(100L)
            .setSourceIpAddresses(podamFactory.manufacturePojo(ArrayList.class, String.class));
        String tenant = RandomStringUtils.randomAlphabetic(10);
        Zone zone = zoneManagement.createPrivateZone(tenant, create);
        assertThat(zone.getId(), notNullValue());
        assertThat(zone.getTenantId(), equalTo(tenant));
        assertThat(zone.getName(), equalTo(create.getName()));
        assertThat(zone.getPollerTimeout(), equalTo(Duration.ofSeconds(create.getPollerTimeout())));
        assertThat(zone.getState(), equalTo(ZoneState.ACTIVE));
        assertFalse(zone.isPublic());

        Optional<Zone> z = zoneManagement.getPrivateZone(tenant, create.getName());
        assertTrue(z.isPresent());
        assertThat(z.get().getName(), equalTo(create.getName()));
    }

    @Test
    public void testCreatePrivateZoneNonAlphanumericName() {
        // This should be successful because the alphanumeric validation only happens via Spring MVC.
        ZoneCreatePrivate create = podamFactory.manufacturePojo(ZoneCreatePrivate.class);
        create.setName("onlyAlphaNumericAreAllowed!!!");
        String tenant = RandomStringUtils.randomAlphabetic(10);
        Zone zone = zoneManagement.createPrivateZone(tenant, create);
        assertThat(zone.getId(), notNullValue());
        assertThat(zone.getTenantId(), equalTo(tenant));
        assertThat(zone.getName(), equalTo(create.getName()));
        assertThat(zone.getPollerTimeout(), equalTo(Duration.ofSeconds(create.getPollerTimeout())));
        assertFalse(zone.isPublic());

        Optional<Zone> z = zoneManagement.getPrivateZone(tenant, create.getName());
        assertTrue(z.isPresent());
        assertThat(z.get().getName(), equalTo(create.getName()));
    }

    @Test
    public void testCreatePublicZone() {
        ZoneCreatePublic create = new ZoneCreatePublic()
            .setName(ResolvedZone.PUBLIC_PREFIX + RandomStringUtils.randomAlphanumeric(6))
            .setProvider(RandomStringUtils.randomAlphanumeric(6))
            .setProviderRegion(RandomStringUtils.randomAlphanumeric(6))
            .setPollerTimeout(100L)
            .setSourceIpAddresses(podamFactory.manufacturePojo(ArrayList.class, String.class));

        Zone zone = zoneManagement.createPublicZone(create);
        assertThat(zone.getId(), notNullValue());
        assertThat(zone.getTenantId(), equalTo(ResolvedZone.PUBLIC));
        assertThat(zone.getName(), equalTo(create.getName()));
        assertThat(zone.getProvider(), equalTo(create.getProvider()));
        assertThat(zone.getProviderRegion(), equalTo(create.getProviderRegion()));
        assertThat(zone.getState(), equalTo(ZoneState.INACTIVE));
        assertThat(zone.getSourceIpAddresses(), hasSize(create.getSourceIpAddresses().size()));
        // we create a new arraylist here because the first param is a PersistentBag and `equals` does not work
        // when comparing to the create arraylist.
        assertThat(new ArrayList<>(zone.getSourceIpAddresses()), equalTo(create.getSourceIpAddresses()));

        assertThat(zone.getState(), equalTo(create.getState()));
        assertThat(zone.getPollerTimeout(), equalTo(Duration.ofSeconds(create.getPollerTimeout())));
        assertTrue(zone.isPublic());

        Optional<Zone> z = zoneManagement.getPublicZone(create.getName());
        assertTrue(z.isPresent());
        assertThat(z.get().getName(), equalTo(create.getName()));
    }

    @Test
    public void testUpdatePublicZone() {
        Zone original = zoneManagement.getAvailableZonesForTenant(ResolvedZone.PUBLIC).get(0);

        assertThat(original, notNullValue());

        ZoneUpdate update = new ZoneUpdate().setPollerTimeout(original.getPollerTimeout().getSeconds() + 100);

        Zone zone = zoneManagement.updatePublicZone(original.getName(), update);
        assertThat(zone.getId(), equalTo(original.getId()));
        assertThat(zone.getTenantId(), equalTo(original.getTenantId()));
        assertThat(zone.getName(), equalTo(original.getName()));
        assertThat(zone.getPollerTimeout(), equalTo(Duration.ofSeconds(update.getPollerTimeout())));

        Optional<Zone> z = zoneManagement.getPublicZone(zone.getName());
        assertTrue(z.isPresent());
        assertThat(z.get().getPollerTimeout().getSeconds(), equalTo(update.getPollerTimeout()));
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
        createPrivateZonesForTenant(privateCount, tenant);
        assertThat(zoneManagement.getAvailableZonesForTenant(tenant), hasSize(1 + privateCount));

        // new public zones should be visible too
        createPublicZones(publicCount);
        assertThat(zoneManagement.getAvailableZonesForTenant(tenant), hasSize(1 + privateCount + publicCount));

        // Another tenant can only see public zones
        assertThat(zoneManagement.getAvailableZonesForTenant(unrelatedTenant), hasSize(1 + publicCount));
    }

    @Test
    public void testGetMonitorsForZone() {
        int count = 12;
        String tenant = RandomStringUtils.randomAlphabetic(10);
        String zone = RandomStringUtils.randomAlphabetic(10);
        assertThat(zoneManagement.getMonitorsForZone(tenant, zone), hasSize(0));

        createRemoteMonitorsForTenant(count, tenant, zone);
        createRemoteMonitorsForTenant(count, tenant, "notMyZone");

        assertThat(zoneManagement.getMonitorsForZone(tenant, zone), hasSize(count));
    }

    @Test
    public void testDeleteEmptyPrivateZone() {
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        Zone newZone = createPrivateZoneForTenant(tenantId);

        when(zoneStorage.getActiveEnvoyCountForZone(any()))
            .thenReturn(CompletableFuture.completedFuture(0L));

        Optional<Zone> zone = zoneManagement.getPrivateZone(tenantId, newZone.getName());
        assertTrue(zone.isPresent());
        assertThat(zone.get(), notNullValue());

        zoneManagement.removePrivateZone(tenantId, newZone.getName());
        zone = zoneManagement.getPrivateZone(tenantId, newZone.getName());
        assertTrue(!zone.isPresent());
    }

    @Test(expected = ZoneDeletionNotAllowed.class)
    public void testDeleteNonEmptyPrivateZone() {
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        Zone newZone = createPrivateZoneForTenant(tenantId);
        when(zoneStorage.getActiveEnvoyCountForZone(any()))
            .thenReturn(CompletableFuture.completedFuture(1L));

        Optional<Zone> zone = zoneManagement.getPrivateZone(tenantId, newZone.getName());
        assertTrue(zone.isPresent());
        assertThat(zone.get(), notNullValue());

        zoneManagement.removePrivateZone(tenantId, newZone.getName());
    }

    @Test
    public void testDeleteEmptyPublicZone() {
        Zone newZone = createPublicZone();

        when(zoneStorage.getActiveEnvoyCountForZone(any()))
            .thenReturn(CompletableFuture.completedFuture(0L));

        Optional<Zone> zone = zoneManagement.getPublicZone(newZone.getName());
        assertTrue(zone.isPresent());
        assertThat(zone.get(), notNullValue());

        zoneManagement.removePublicZone(newZone.getName());
        zone = zoneManagement.getPublicZone(newZone.getName());
        assertTrue(!zone.isPresent());
    }

    @Test(expected = ZoneDeletionNotAllowed.class)
    public void testDeleteNonEmptyPublicZone() {
        Zone newZone = createPublicZone();
        when(zoneStorage.getActiveEnvoyCountForZone(any()))
            .thenReturn(CompletableFuture.completedFuture(1L));

        Optional<Zone> zone = zoneManagement.getPublicZone(newZone.getName());
        assertTrue(zone.isPresent());
        assertThat(zone.get(), notNullValue());

        zoneManagement.removePublicZone(newZone.getName());
    }

    @Test
    public void testGetMonitorCountForZone() {
        int privateCount = 13;
        int publicCount = 17;
        String tenant = RandomStringUtils.randomAlphabetic(10);
        String privateZone = RandomStringUtils.randomAlphabetic(10);
        String publicZone = ResolvedZone.PUBLIC_PREFIX + RandomStringUtils.randomAlphabetic(6);

        assertThat(zoneManagement.getMonitorCountForPrivateZone(tenant, privateZone), equalTo(0));
        assertThat(zoneManagement.getMonitorCountForPublicZone(publicZone), equalTo(0));

        createRemoteMonitorsForTenant(privateCount, tenant, privateZone);
        createRemoteMonitorsForTenant(privateCount + 2, tenant, "anotherPrivateZone");
        createRemoteMonitorsForTenant(publicCount, tenant, publicZone);

        assertThat(zoneManagement.getMonitorCountForPrivateZone(tenant, privateZone), equalTo(privateCount));
        assertThat(zoneManagement.getMonitorCountForPublicZone(publicZone), equalTo(publicCount));
    }
}