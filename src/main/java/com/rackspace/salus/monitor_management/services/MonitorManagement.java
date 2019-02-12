package com.rackspace.salus.monitor_management.services;

import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.repositories.ResourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Set;

@Slf4j
@Service
public class MonitorManagement {

    // if we need this it is probably supposed to be a MonitorRepository
    private final ResourceRepository resourceRepository;
    //private final KafkaEgress kafkaEgress;

    @PersistenceContext
    private final EntityManager entityManager;

    private static final String ENVOY_NAMESPACE = "envoy.";

    @Autowired
    public MonitorManagement(ResourceRepository resourceRepository, EntityManager entityManager) { //KafkaEgress kafkaEgress,
        this.resourceRepository = resourceRepository;
        //this.kafkaEgress = kafkaEgress;
        this.entityManager = entityManager;
    }


    public void handleResourceEvent(ResourceEvent event) {
        log.debug("");
        Set<String> keys = event.getOldLabels().keySet();
        keys.removeAll(event.getResource().getLabels().keySet());//now we should have the difference of labels.
        // Unless we just want to start out by reading in the new list of labels and clobbering the old data that exists.

        // do something with the labels. In this situation I really like creating enums and using them to create the distinct logic.

        // post kafka egress event
    }
}
