package com.rackspace.salus.monitor_management.services;

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ResourceEventListener {


    private final KafkaTopicProperties properties;
    private final MonitorManagement monitorManagement;
    private final String topic;

    @Autowired
    public ResourceEventListener(KafkaTopicProperties properties, MonitorManagement monitorManagement) {
        this.properties = properties;
        this.monitorManagement = monitorManagement;
        this.topic = this.properties.getResources();
    }


    /**
     * This method is used by the __listener.topic magic in the KafkaListener
     * @return The topic to consume
     */
    public String getTopic() {
        return this.topic;
    }


    /*
    So basically what I need to do is this:
    identify what events will be coming in.
    Create functions for each of those events. They might all be under the same topic so I will need to filter somehow.
     */




    /**
     * This receives a resource event from Kafka and passes it to the monitor manager to do whatever is needed.
     * @param resourceEvent The ResourceEvent read from Kafka.
     * @throws Exception
     */
    @KafkaListener(topics = "#{__listener.topic}")
    public void consumeResourceEvents(ResourceEvent resourceEvent) {
        log.debug("Processing new resource event: {}", resourceEvent);

        monitorManagement.handleResourceEvent(resourceEvent);
    }
}
