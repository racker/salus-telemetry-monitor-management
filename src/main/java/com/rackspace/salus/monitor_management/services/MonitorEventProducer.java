package com.rackspace.salus.monitor_management.services;

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.KafkaMessageType;
import com.rackspace.salus.telemetry.messaging.MonitorEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
public class MonitorEventProducer {

    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final KafkaTopicProperties properties;

    @Autowired
    public MonitorEventProducer(KafkaTemplate<String,Object> kafkaTemplate, KafkaTopicProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties= properties;
    }

    // ok so we need to figure out what we are sending
    //unless we want to just pass into this function everything
    public void sendMonitorEvent(MonitorEvent event) {
        final String topic = properties.getMonitors();
        if (topic == null) {
            throw new IllegalArgumentException(String.format("No topic configured for %s", KafkaMessageType.MONITOR));
        }

        //Resource resource = event.getResource();
        //String key = String.format("%s:%s", resource.getTenantId(), resource.getResourceId());
        String key = String.format("%s", event.getAmbassadorId());
        kafkaTemplate.send(topic, key, event);
    }
}