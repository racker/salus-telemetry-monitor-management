package com.rackspace.salus.monitor_management.web.model;

import lombok.Data;

@Data
public class ZoneDTO {
    String name;
    String tenantId;
    long envoyTimeout;
}
