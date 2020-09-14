/*
 * Copyright 2020 Rackspace US, Inc.
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

package com.rackspace.salus.monitor_management.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rackspace.salus.common.config.MetricNames;
import com.rackspace.salus.common.config.MetricTags;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationOperatorCreate;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.MonitorTranslationOperator;
import com.rackspace.salus.telemetry.errors.MonitorContentTranslationException;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.repositories.MonitorTranslationOperatorRepository;
import com.rackspace.salus.telemetry.translators.MonitorTranslator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * This service is responsible for translating the rendered content of {@link BoundMonitor}s into the exact
 * plugin structure expected by a specific version of an agent and returning {@link BoundMonitorDTO}s
 * that contain the translated content.
 * <p>
 * The translation specification and embedded implementation of each are declared as concrete classes
 * extending {@link MonitorTranslator}.
 * </p>
 * <p>
 * The runtime mapping of translators to agent types and versions is persisted via the {@link
 * MonitorTranslationOperator} entity.
 * </p>
 */
@Service
@Slf4j
public class MonitorContentTranslationService {

  private final MonitorTranslationOperatorRepository monitorTranslationOperatorRepository;
  private final ObjectMapper objectMapper;

  MeterRegistry meterRegistry;

  // metrics counters
  private final Counter.Builder translateMonitorContentSuccess;

  @Autowired
  public MonitorContentTranslationService(
      MonitorTranslationOperatorRepository monitorTranslationOperatorRepository,
      ObjectMapper objectMapper, MeterRegistry meterRegistry) {
    this.monitorTranslationOperatorRepository = monitorTranslationOperatorRepository;
    this.objectMapper = objectMapper;

    this.meterRegistry = meterRegistry;
    translateMonitorContentSuccess = Counter.builder(MetricNames.SERVICE_OPERATION_SUCCEEDED)
        .tag(MetricTags.SERVICE_METRIC_TAG,"MonitorContentTranslationService");
  }

  public MonitorTranslationOperator create(MonitorTranslationOperatorCreate in) {
    final MonitorTranslationOperator operator = new MonitorTranslationOperator()
        .setName(in.getName())
        .setDescription(in.getDescription())
        .setAgentType(in.getAgentType())
        .setAgentVersions(in.getAgentVersions())
        .setMonitorType(in.getMonitorType())
        .setSelectorScope(in.getSelectorScope())
        .setTranslatorSpec(in.getTranslatorSpec())
        .setOrder(in.getOrder());

    log.info("Creating new monitorTranslationOperator={}", operator);
    MonitorTranslationOperator monitorTranslationOperator = monitorTranslationOperatorRepository.save(operator);
    translateMonitorContentSuccess.tags(MetricTags.OPERATION_METRIC_TAG, "create",MetricTags.OBJECT_TYPE_METRIC_TAG,"monitorTranslationOperator").register(meterRegistry).increment();
    return monitorTranslationOperator;
  }

  public Page<MonitorTranslationOperator> getAll(Pageable pageable) {
    return monitorTranslationOperatorRepository.findAll(pageable);
  }

  public MonitorTranslationOperator getById(UUID operatorId) {
    return monitorTranslationOperatorRepository.findById(operatorId).orElseThrow(() ->
         new NotFoundException("Could not find monitor translation operator"));
  }

  public void delete(UUID operatorId) {
    log.info("Deleting monitorTranslationOperator={}", operatorId);
    monitorTranslationOperatorRepository.deleteById(operatorId);
  }

  public List<MonitorTranslationDetails> getMonitorTranslationDetails() {
    List<MonitorTranslationOperator> operators = monitorTranslationOperatorRepository.findAll();

    return operators.stream()
        // first ensure everything is in order of priority
        .sorted(Comparator.comparingInt(MonitorTranslationOperator::getOrder))
        // create a hashmap of (agenttype/monitortype:translations)
        .collect(Collectors.toMap(op -> new MonitorTranslationDetails().setAgentType(op.getAgentType()).setMonitorType(op.getMonitorType()),
            op -> List.of(op.getTranslatorSpec().info()),
            ListUtils::union))
        // convert the map into a stream of details
        .entrySet().stream().map(entry -> entry.getKey().setTranslations(entry.getValue()))
        // then sort it by agent type and then monitor type
        .sorted(Comparator.comparing(MonitorTranslationDetails::getAgentType, Comparator.comparing(Enum::toString))
            .thenComparing(v -> v.getMonitorType().toString()))
        // then build the final list.
        .collect(Collectors.toList());
  }

  public List<BoundMonitorDTO> translateBoundMonitors(List<BoundMonitor> boundMonitors,
                                                      Map<AgentType, String> agentVersions) {

    if (CollectionUtils.isEmpty(agentVersions)) {
      // No agents installed, so it's not possible to perform any translation of bound monitors.
      // Agent installation is triggered on every Envoy attachment, so the bound monitor translation
      // is attempted again when the agent install details are known.
      return List.of();
    }

    final Map<AgentType, List<MonitorTranslationOperator>> operatorsByAgentType =
        loadOperatorsByAgentTypeAndVersion(agentVersions);

    // now translate the rendered content of the bound monitors and turn them into DTOs
    return boundMonitors.stream()
        .map(boundMonitor -> {
          try {
            BoundMonitorDTO boundMonitorDTO = new BoundMonitorDTO(boundMonitor)
                .setRenderedContent(
                    translateMonitorContent(
                        prepareOperatorsForMonitor(
                            operatorsByAgentType.get(boundMonitor.getMonitor().getAgentType()),
                            boundMonitor.getMonitor().getMonitorType(),
                            boundMonitor.getMonitor().getSelectorScope()
                        ),
                        boundMonitor.getRenderedContent()
                    )
                );
            return boundMonitorDTO;
          } catch (MonitorContentTranslationException e) {
            log.error("Failed to translate boundMonitor={}", boundMonitor, e);
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Re-map the requested agent-versions into a list of operators each, where those are initially
   * retrieved from the DB
   * @param agentVersions the mapping of installed agent types and versions
   * @return the mapping of agent types to conversion operators for the given version of each
   */
  public Map<AgentType, List<MonitorTranslationOperator>> loadOperatorsByAgentTypeAndVersion(
      Map<AgentType, String> agentVersions) {
    return agentVersions.entrySet().stream()
        .collect(Collectors.toMap(
            Entry::getKey,
            entry -> loadOperators(entry.getKey(), entry.getValue())
        ));
  }

  /**
   * Converts a bound monitor's content from the format received via an api request
   * to what is expected by the agent that will run it (e.g. telegraf).
   *
   * @param operators The list of translate operations to perform on the monitor.
   * @param monitorContent the template-rendered monitor/plugin content
   * @return the converted monitor/plugin content. If no operators applied, then the given
   * monitorContent is returned
   * @throws MonitorContentTranslationException when unable to properly execute conversion operators
   */
  public String translateMonitorContent(List<MonitorTranslationOperator> operators,
                                        String monitorContent)
      throws MonitorContentTranslationException {

    // If no operators apply, then build DTO without translation
    if (operators == null || operators.isEmpty()) {
      return monitorContent;
    }

    ObjectNode contentTree;
    try {
      contentTree = (ObjectNode) objectMapper.readTree(monitorContent);
    } catch (IOException e) {
      throw new MonitorContentTranslationException("Unable to parse monitor content as JSON", e);
    } catch (ClassCastException e) {
      throw new MonitorContentTranslationException(
          "Content did not contain an object structure", e);
    }

    for (MonitorTranslationOperator operator : operators) {
      operator.getTranslatorSpec().translate(contentTree);
    }

    if (!contentTree.hasNonNull(MonitorTranslator.TYPE_PROPERTY)) {
      throw new MonitorContentTranslationException(
          "Content translation resulted is missing JSON type property");
    }

    // swap out rendered content with rendered->translated content
    try {
      String translatedMonitorContent = objectMapper.writeValueAsString(contentTree);
      translateMonitorContentSuccess.tags(MetricTags.OPERATION_METRIC_TAG, "translate",MetricTags.OBJECT_TYPE_METRIC_TAG,"monitorContent").register(meterRegistry).increment();
      return translatedMonitorContent;
    } catch (JsonProcessingException e) {
      throw new MonitorContentTranslationException(
          String.format("Failed to serialize translated contentTree=%s", contentTree), e);
    }
  }

  /**
   * Retrieves {@link MonitorTranslationOperator} entities that match the given agentType
   * and narrows those results by those that have a version range that satisfies the given agentVersion
   */
  private List<MonitorTranslationOperator> loadOperators(AgentType agentType, String agentVersion) {
    final ArtifactVersion agentSemVer = new DefaultArtifactVersion(agentVersion);

    return
        monitorTranslationOperatorRepository.findAllByAgentType(agentType).stream()
            .filter(op ->
                {
                  // match any
                  if (op.getAgentVersions() == null) {
                    return true;
                  }

                  // or match within range
                  try {
                    return VersionRange.createFromVersionSpec(op.getAgentVersions())
                            .containsVersion(agentSemVer);
                  } catch (InvalidVersionSpecificationException e) {
                    log.warn("op={} contained an invalid version range specification", op, e);
                    return false;
                  }
                }
            )
            .collect(Collectors.toList());
  }

  /**
   * Filters and orders the given operators for just the specifically given monitor type and scope.
   * @param operators the superset of operators for an agent type and version
   * @param monitorType the monitor type to evaluate
   * @param selectorScope the scope to evaluate
   * @return operators filtered and ordered for given monitor type and scope
   */
  public List<MonitorTranslationOperator> prepareOperatorsForMonitor(
      List<MonitorTranslationOperator> operators, MonitorType monitorType,
      ConfigSelectorScope selectorScope) {

    return operators.stream()
        .filter(o -> o.getMonitorType() == null || o.getMonitorType() == monitorType)
        .filter(o -> o.getSelectorScope() == null || o.getSelectorScope() == selectorScope)
        .sorted(Comparator.comparingInt(MonitorTranslationOperator::getOrder))
        .collect(Collectors.toList());
  }

}
