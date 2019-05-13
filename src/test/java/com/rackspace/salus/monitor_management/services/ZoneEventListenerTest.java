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

package com.rackspace.salus.monitor_management.services;

import static org.mockito.Mockito.verify;

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.NewResourceZoneEvent;
import com.rackspace.salus.telemetry.messaging.ReattachedResourceZoneEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ZoneEventListenerTest {

  @Mock
  MonitorManagement monitorManagement;
  private ZoneEventListener zoneEventListener;

  @Before
  public void setUp() throws Exception {
    KafkaTopicProperties topicProperties = new KafkaTopicProperties();
    zoneEventListener = new ZoneEventListener(topicProperties, monitorManagement);
  }

  @Test
  public void testZoneNewResourceEvent() {
    zoneEventListener.handleEvent(
        new NewResourceZoneEvent()
        .setTenantId("t-1")
        .setZoneName("z-1")
    );

    verify(monitorManagement).handleNewEnvoyInZone("t-1", "z-1");
  }

  @Test
  public void testZoneEnvoyOfResourceChangedEvent() {
    zoneEventListener.handleEvent(
        new ReattachedResourceZoneEvent()
            .setFromEnvoyId("e-1")
            .setToEnvoyId("e-2")
            .setTenantId("t-1")
            .setZoneName("z-1")
    );

    verify(monitorManagement).handleEnvoyResourceChangedInZone(
        "t-1", "z-1", "e-1", "e-2");
  }
}