package com.rackspace.salus.monitor_management.services;

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.MonitorEvent;
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

    public void sendMonitorEvent(MonitorEvent event) {
        final String topic = properties.getMonitors();

        kafkaTemplate.send(topic, event.getEnvoyId(), event);
    }

    public void sendMonitorEvent(MonitorBoundEvent event) {
        final String topic = properties.getMonitors();

        log.debug("Sending monitorBoundEvent={} on topic={}", event, topic);
        kafkaTemplate.send(topic, event.getEnvoyId(), event);
    }
}