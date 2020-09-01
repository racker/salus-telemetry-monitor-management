package com.rackspace.salus.monitor_management.web.model;

import com.rackspace.salus.telemetry.entities.Monitor;
import lombok.Data;

@Data
public class RenderedMonitorTemplate {

  private Monitor monitor;

  private String renderedContent;
}
