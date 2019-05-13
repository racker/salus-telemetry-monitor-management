/*
 * Copyright 2019 Rackspace US, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rackspace.salus.monitor_management.entities;

import com.rackspace.salus.monitor_management.web.model.MonitorDTO;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@Entity
@Table(name = "monitors")
@Data
public class Monitor implements Serializable {
    @Id
    @GeneratedValue
    @org.hibernate.annotations.Type(type="uuid-char")
    UUID id;

    @Column(name="monitor_name")
    String monitorName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name="monitor_label_selectors", joinColumns = @JoinColumn(name="monitor_id"))
    Map<String,String> labelSelector;

    @NotBlank
    @Column(name="tenant_id")
    String tenantId;

    @NotBlank
    String content;

    @NotNull
    @Column(name="agent_type")
    @Enumerated(EnumType.STRING)
    AgentType agentType;

    @Column(name="selector_scope")
    @Enumerated(EnumType.STRING)
    ConfigSelectorScope selectorScope;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name="monitor_zones", joinColumns = @JoinColumn(name="monitor_id"))
    List<String> zones;

    public MonitorDTO toDTO() {
      return new MonitorDTO()
          .setId(id)
          .setMonitorName(monitorName)
          .setLabelSelector(new HashMap<>(labelSelector))
          .setTenantId(tenantId)
          .setContent(content)
          .setAgentType(agentType)
          .setSelectorScope(selectorScope)
          .setZones(new ArrayList<>(zones));
    }
}
