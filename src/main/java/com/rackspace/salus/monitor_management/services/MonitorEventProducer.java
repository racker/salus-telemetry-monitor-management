package com.rackspace.salus.monitor_management.services;

import static com.rackspace.salus.common.messaging.KafkaMessageKeyBuilder.buildMessageKey;

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.PolicyMonitorUpdateEvent;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class MonitorEventProducer {

    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final KafkaTopicProperties properties;

    @Autowired
    public MonitorEventProducer(KafkaTemplate<String,Object> kafkaTemplate, KafkaTopicProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties= properties;
    }

    public void sendMonitorEvent(MonitorBoundEvent event) {
        final String topic = properties.getMonitors();

        log.debug("Sending monitorBoundEvent={} on topic={}", event, topic);
        kafkaTemplate.send(topic, event.getEnvoyId(), event);
    }

    public void sendPolicyMonitorUpdateEvent(PolicyMonitorUpdateEvent event) {
        final String topic = properties.getPolicies();

        log.debug("Sending policyMonitorUpdateEvent={} on topic={}", event, topic);
        kafkaTemplate.send(topic, buildMessageKey(event), event); // will this work or will a null value break the key? could just tell it to use monitorId
    }
}