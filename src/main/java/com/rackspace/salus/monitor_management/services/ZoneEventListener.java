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

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.NewResourceZoneEvent;
import com.rackspace.salus.telemetry.messaging.ReattachedResourceZoneEvent;
import com.rackspace.salus.telemetry.messaging.ZoneEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * This services consumes zone events from Kafka and interacts with the internal services of
 * monitor management to adapt to the changes conveyed by those events.
 */
@Service
@Slf4j
public class ZoneEventListener {

  private final String topic;
  private final MonitorManagement monitorManagement;

  @Autowired
  public ZoneEventListener(KafkaTopicProperties topicProperties, MonitorManagement monitorManagement) {
    this.topic = topicProperties.getZones();
    this.monitorManagement = monitorManagement;
  }

  public String getTopic() {
    return topic;
  }

  @KafkaListener(topics = "#{__listener.topic}")
  public void handleEvent(ZoneEvent zoneEvent) {
    log.debug("Handling zone event={}", zoneEvent);

    if (zoneEvent instanceof NewResourceZoneEvent) {
      final NewResourceZoneEvent event = (NewResourceZoneEvent) zoneEvent;
      monitorManagement.handleNewEnvoyInZone(
          event.getTenantId(),
          event.getZoneId()
      );
    } else if (zoneEvent instanceof ReattachedResourceZoneEvent) {
      final ReattachedResourceZoneEvent event = (ReattachedResourceZoneEvent) zoneEvent;

      monitorManagement.handleEnvoyResourceChangedInZone(
          event.getTenantId(),
          event.getZoneId(),
          event.getFromEnvoyId(),
          event.getToEnvoyId()
      );
    } else {
      log.warn("Discarding unknown ZoneEvent={}", zoneEvent);
    }
  }
}
