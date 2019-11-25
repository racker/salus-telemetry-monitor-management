package com.rackspace.salus.monitor_management.web.model.oracle;

import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.ApplicableMonitorType;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApplicableAgentType(AgentType.ORACLE)
@ApplicableMonitorType(MonitorType.tablespace)
public class Tablespace extends LocalPlugin {
  List<String> databaseNames;
  String filePath;
}
