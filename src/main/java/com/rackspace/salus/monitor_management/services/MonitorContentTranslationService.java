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

package com.rackspace.salus.monitor_management.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationOperatorCreate;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.MonitorTranslationOperator;
import com.rackspace.salus.telemetry.errors.MonitorContentTranslationException;
import com.rackspace.salus.telemetry.model.AgentType;
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

  // metric counters
  private final Counter monitorTranslateErrors;

  @Autowired
  public MonitorContentTranslationService(
      MonitorTranslationOperatorRepository monitorTranslationOperatorRepository,
      ObjectMapper objectMapper, MeterRegistry meterRegistry) {
    this.monitorTranslationOperatorRepository = monitorTranslationOperatorRepository;
    this.objectMapper = objectMapper;

    this.monitorTranslateErrors = meterRegistry.counter("errors",
        "operation", "monitorTranslate");
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
    return monitorTranslationOperatorRepository.save(operator);
  }

  public Page<MonitorTranslationOperator> getAll(Pageable pageable) {
    return monitorTranslationOperatorRepository.findAll(pageable);
  }

  public MonitorTranslationOperator getById(UUID operatorId) {
    return monitorTranslationOperatorRepository.findById(operatorId)
        .orElseThrow(() -> new NotFoundException("Could not find monitor translation operator"));
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
        .collect(Collectors.toMap((MonitorTranslationOperator op) ->
                new MonitorTranslationOperator().setAgentType(op.getAgentType()).setMonitorType(op.getMonitorType()),
            (MonitorTranslationOperator op) -> List.of(op.getTranslatorSpec().info()),
            ListUtils::union))
        // convert the map into a stream of details
        .entrySet().stream().map(entry ->
            new MonitorTranslationDetails().setAgentType(entry.getKey().getAgentType())
            .setMonitorType(entry.getKey().getMonitorType())
            .setTranslations(entry.getValue()))
        // then sort it by agent type and then monitor type
        .sorted(Comparator.comparing(MonitorTranslationDetails::getAgentType, Comparator.comparing(Enum::toString))
            .thenComparing(v -> v.getMonitorType().toString()))
        // then build the final list.
        .collect(Collectors.toList());
  }

  public List<BoundMonitorDTO> translate(List<BoundMonitor> boundMonitors,
                                         Map<AgentType, String> agentVersions) {

    if (CollectionUtils.isEmpty(agentVersions)) {
      // No agents installed, so it's not possible to perform any translation of bound monitors.
      // Agent installation is triggered on every Envoy attachment, so the bound monitor translation
      // is attempted again when the agent install details are known.
      return List.of();
    }

    // re-map the requested agent-versions into a list of operators each, where those are initially
    // retrieved from the DB
    final Map<AgentType, List<MonitorTranslationOperator>> operatorsByAgentType =
        agentVersions.entrySet().stream()
            .collect(Collectors.toMap(
                Entry::getKey,
                entry -> loadOperators(entry.getKey(), entry.getValue())
            ));

    // now translate the rendered content of the bound monitors and turn them into DTOs
    return boundMonitors.stream()
        .map(boundMonitor -> {
          try {
            return translateBoundMonitor(
                boundMonitor,
                getOperatorsForMonitor(operatorsByAgentType, boundMonitor)
            );
          } catch (MonitorContentTranslationException e) {
            log.error("Failed to translate boundMonitor={}", boundMonitor, e);
            monitorTranslateErrors.increment();
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Converts a bound monitor's content from the format received via an api request
   * to what is expected by the agent that will run it (e.g. telegraf).
   *
   * @param boundMonitor The bound monitor to convert
   * @param operators The list of translate operations to perform on the monitor.
   * @return The bound monitor with an updated content string.
   * @throws MonitorContentTranslationException
   */
  private BoundMonitorDTO translateBoundMonitor(BoundMonitor boundMonitor,
                                       List<MonitorTranslationOperator> operators)
      throws MonitorContentTranslationException {

    // If no operators apply, then build DTO without translation
    if (operators == null || operators.isEmpty()) {
      return new BoundMonitorDTO(boundMonitor);
    }

    final String monitorContent = boundMonitor.getRenderedContent();

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

    final BoundMonitorDTO boundMonitorDTO = new BoundMonitorDTO(boundMonitor);

    // swap out rendered content with rendered->translated content
    try {

      boundMonitorDTO.setRenderedContent(objectMapper.writeValueAsString(contentTree));

      return boundMonitorDTO;

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
            .sorted(MonitorContentTranslationService::highestPrecedenceFirst)
            .collect(Collectors.toList());
  }

  private List<MonitorTranslationOperator> getOperatorsForMonitor(
      Map<AgentType, List<MonitorTranslationOperator>> operatorsByAgentType,
      BoundMonitor bound) {

    List<MonitorTranslationOperator> operators = operatorsByAgentType.get(bound.getMonitor().getAgentType());
    return operators.stream()
        .filter(o -> o.getMonitorType() == null || o.getMonitorType() == bound.getMonitor().getMonitorType())
        .filter(o -> o.getSelectorScope() == null || o.getSelectorScope() == bound.getMonitor().getSelectorScope())
        .sorted(Comparator.comparingInt(MonitorTranslationOperator::getOrder))
        .collect(Collectors.toList());
  }

  private static int highestPrecedenceFirst(MonitorTranslationOperator lhs,
                                            MonitorTranslationOperator rhs) {
    if (lhs.getAgentVersions() == null && rhs.getAgentVersions() == null) {
      return 0;
    } else if (lhs.getAgentVersions() == null || rhs.getAgentVersions() == null) {
      // ones with null versions are less specific, so sorted "greater than"
      return rhs.getAgentVersions() != null ? 1 : -1;
    } else {
      // When both have versions, resort to a reverse textual comparison trying to put something
      // like ">=1.12" ahead of ">=1.11". The ordering within these cases is not particularly
      // important since loadOperators will filter down to operators that apply to the
      // agent versions anyway.
      return rhs.getAgentVersions().compareTo(lhs.getAgentVersions());
    }
  }
}
