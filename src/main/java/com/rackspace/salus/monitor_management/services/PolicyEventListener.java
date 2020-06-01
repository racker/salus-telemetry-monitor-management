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

import static com.rackspace.salus.telemetry.entities.Monitor.POLICY_TENANT;

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.MetadataPolicyEvent;
import com.rackspace.salus.telemetry.messaging.MonitorPolicyEvent;
import com.rackspace.salus.telemetry.messaging.TenantPolicyChangeEvent;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@KafkaListener(topics = "#{__listener.topic}")
public class PolicyEventListener {

  private final KafkaTopicProperties properties;
  private final MonitorManagement monitorManagement;
  private final String topic;

  @Autowired
  public PolicyEventListener(KafkaTopicProperties properties, MonitorManagement monitorManagement) {
    this.properties = properties;
    this.monitorManagement = monitorManagement;
    this.topic = this.properties.getPolicies();
  }

  /**
   * This method is used by the __listener.topic magic in the KafkaListener
   * @return The topic to consume
   */
  public String getTopic() {
    return this.topic;
  }

  /**
   * This receives a policy event from Kafka and passes it to the monitor manager to do whatever is needed.
   * @param policyEvent The MonitorPolicyEvent read from Kafka.
   * @throws Exception
   */
  @KafkaHandler
  @Transactional
  public void consumeMonitorPolicyEvents(MonitorPolicyEvent policyEvent) {
    log.debug("Processing monitor policy event: {}", policyEvent);

    monitorManagement.handleMonitorPolicyEvent(policyEvent);
  }

  @KafkaHandler
  public void consumeMetadataPolicyUpdateEvents(MetadataPolicyEvent policyEvent) {
    if (policyEvent.getTenantId().equals(POLICY_TENANT)) {
      // Policy Monitors should not contain metadata.
      log.error("Received MetadataPolicyEvent={} for policy tenant", policyEvent);
    } else {
      monitorManagement.handleMetadataPolicyEvent(policyEvent);
    }
  }

  @KafkaHandler
  @Transactional
  public void consumeTenantChangeEvents(TenantPolicyChangeEvent tenantEvent) {
    monitorManagement.handleTenantChangeEvent(tenantEvent);
  }

  /**
   * The policy topic contains multiple event types.
   * This service does not have to act on them all, so we just ignore them if seen.
   * @param event The event we will be ignoring.
   */
  @KafkaHandler(isDefault = true)
  public void ignoreUnhandledEvents(Object event) {
    log.trace("Ignoring event={} with no handler", event);
  }
}
