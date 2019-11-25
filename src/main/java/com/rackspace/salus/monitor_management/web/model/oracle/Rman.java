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
@ApplicableMonitorType(MonitorType.rman)
public class Rman extends LocalPlugin {
  List<String> exclusionCodes;
  List<String> databaseNames;
  String filePath;
}
