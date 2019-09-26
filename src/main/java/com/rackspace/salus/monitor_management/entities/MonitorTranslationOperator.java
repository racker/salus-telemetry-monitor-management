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

import com.rackspace.salus.monitor_management.web.model.translators.MonitorTranslator;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

/**
 * This entity persists a specific instance of a monitor translation for a given agent type and
 * optionally agent versions, monitor type, and/or selector scope.
 */
@Entity
@Table(name = "monitor_translation_operators", indexes = {
    @Index(name = "monitor_translation_operators_by_agent_type", columnList = "agent_type")
})
@TypeDef(name = "monitorTypeJsonString", typeClass = MonitorTranslatorJsonStringType.class)
@Data
public class MonitorTranslationOperator {

  @Id
  @GeneratedValue
  @org.hibernate.annotations.Type(type="uuid-char")
  UUID id;

  @Column(name = "agent_type", nullable = false)
  @Enumerated(EnumType.STRING)
  AgentType agentType;

  /**
   * Optional field that conveys an applicable version ranges
   * <a href="https://github.com/zafarkhaja/jsemver#external-dsl"></a>in the form of jsemver's external DSL</a>
   */
  @Column(name = "agent_versions")
  String agentVersions;

  /**
   * Optional field that narrows applicability to a specific monitor type.
   */
  @Column(name = "monitor_type")
  MonitorType monitorType;

  /**
   * Optional field that narrows applicability to a specific selector scope.
   */
  @Column(name="selector_scope")
  ConfigSelectorScope selectorScope;

  /**
   * Persisted column contains the JSON serialization of a concrete subclass of {@link MonitorTranslator}
   */
  @Column(name = "translator_spec", nullable = false, length = 500)
  @Type(type = "monitorTypeJsonString")
  MonitorTranslator translatorSpec;
}
