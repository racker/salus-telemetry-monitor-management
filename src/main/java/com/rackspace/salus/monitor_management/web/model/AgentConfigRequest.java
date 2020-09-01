package com.rackspace.salus.monitor_management.web.model;

import com.rackspace.salus.telemetry.model.AgentType;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class AgentConfigRequest {

  @NotNull
  private AgentType agentType;
  @NotBlank
  private String agentVersion;

  private String resourceId;
}
