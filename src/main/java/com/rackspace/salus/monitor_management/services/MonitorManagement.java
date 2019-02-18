package com.rackspace.salus.monitor_management.services;

import com.rackspace.salus.telemetry.messaging.MonitorEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.AgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Set;

@Slf4j
@Service
public class MonitorManagement {
    private final MonitorEventProducer monitorEventProducer;

    @Autowired
    public MonitorManagement(MonitorEventProducer monitorEventProducer) {
        this.monitorEventProducer = monitorEventProducer;
    }


    public void handleResourceEvent(ResourceEvent event) {
        log.debug("");
        if (event.getOldLabels() != null) {
            Set<String> keys = event.getOldLabels().keySet();
            keys.removeAll(event.getResource().getLabels().keySet());//now we should have the difference of labels.
        }
        /*
            We probably want to grab three different lists of labels. Deleted labels (set difference on the oldLabels),
            added labels (set difference on the new labels), and the labels that stayed the same (possibly updated?)

            Unless we just want to start out by reading in the new list of labels and clobbering the old data that exists.

            When we do something with them this feels a little like a state machine which is really well suited to functions
            attached to enums. But its probably fine to just grab the different lists and pass them off to their respective SQL
            functions
        */


        // post kafka egress event. This will probably be handled post CRUD event, and not in this function.
        MonitorEvent monitorEvent = new MonitorEvent();
        monitorEvent.setTenantId(event.getResource().getTenantId());
        monitorEvent.setOperationType(event.getOperation());
        // monitorEvent.setEnvoyId()
        // monitorEvent.setAmbassadorId()
        AgentConfig config = new AgentConfig();
        config.setLabels(event.getResource().getLabels());
        config.setContent("this is sample content");
        monitorEvent.setConfig(config);
        monitorEventProducer.sendMonitorEvent(monitorEvent);
    }
}
