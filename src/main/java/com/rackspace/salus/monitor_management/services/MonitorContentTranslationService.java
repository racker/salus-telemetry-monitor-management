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
import com.github.zafarkhaja.semver.Version;
import com.rackspace.salus.monitor_management.entities.MonitorTranslationOperator;
import com.rackspace.salus.monitor_management.repositories.MonitorTranslationOperatorRepository;
import com.rackspace.salus.monitor_management.translators.MonitorTranslator;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationOperatorCreate;
import com.rackspace.salus.monitor_management.web.model.translators.MonitorTranslatorSpec;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.NotFoundException;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This service is responsible for translating the rendered content of bound monitors into the exact
 * plugin structure expected by a specific version of an agent.
 * <p>
 * The translation types and embedded implementation of each are declared as concrete classes
 * extending {@link MonitorTranslatorSpec}.
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
  private final Map<
        Class<? extends MonitorTranslatorSpec>,
        MonitorTranslator<? extends MonitorTranslatorSpec>
      > specToImpl = new HashMap<>();

  @Autowired
  public MonitorContentTranslationService(
      MonitorTranslationOperatorRepository monitorTranslationOperatorRepository,
      ObjectMapper objectMapper,
      List<MonitorTranslator<?>> monitorTranslators) {
    this.monitorTranslationOperatorRepository = monitorTranslationOperatorRepository;
    this.objectMapper = objectMapper;

    indexMonitorTranslators(monitorTranslators);
  }

  private void indexMonitorTranslators(List<MonitorTranslator<?>> monitorTranslators) {

    /*
    This following code has the goal of introspecting out the "SomeSpec" from a declaration that
    looks like this

    @Component
    public class SomeTranslator implements MonitorTranslator<SomeSpec>

    That "spec" class is used as a key into specToImpl to allow for later looking up the translator
    given the spec deserialized from a particular operator.
     */

    for (MonitorTranslator<?> monitorTranslator : monitorTranslators) {
      for (Type genericInterface : monitorTranslator.getClass().getGenericInterfaces()) {
        if (genericInterface instanceof ParameterizedType) {
          final ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
          if (parameterizedType.getRawType().equals(MonitorTranslator.class) &&
              parameterizedType.getActualTypeArguments()[0] instanceof Class) {
            //noinspection unchecked
            specToImpl.put(
                (Class<? extends MonitorTranslatorSpec>) parameterizedType.getActualTypeArguments()[0], monitorTranslator);
          }
        }
      }
    }
  }

  public MonitorTranslationOperator save(MonitorTranslationOperatorCreate in) {
    final MonitorTranslationOperator operator = new MonitorTranslationOperator()
        .setAgentType(in.getAgentType())
        .setAgentVersions(in.getAgentVersions())
        .setTranslatorSpec(in.getTranslatorSpec());

    log.info("Saving new monitorTranslationOperator={}", operator);
    return monitorTranslationOperatorRepository.save(operator);
  }

  public MonitorTranslationOperator retrieve(UUID operatorId) {
    return monitorTranslationOperatorRepository.findById(operatorId)
        .orElseThrow(() -> new NotFoundException("Could not find monitor translation operator"));
  }

  public void delete(UUID operatorId) {
    monitorTranslationOperatorRepository.deleteById(operatorId);
  }

  public List<BoundMonitorDTO> translate(List<BoundMonitor> boundMonitors,
                                         Map<AgentType, String> agentVersions) {

    if (agentVersions == null || agentVersions.isEmpty()) {
      // No agents installed, so it's not possible to perform any translation of bound monitors
      // A later agent installation will circle back to this operation with a non-emtpy agent version map.
      return List.of();
    }

    // re-map the requested agent-versions into translator instances, where those are initially
    // retrieved from the DB
    final Map<AgentType, List<MonitorTranslationOperator>> operatorsByAgentType =
        agentVersions.entrySet().stream()
            .collect(Collectors.toMap(
                Entry::getKey,
                entry -> loadTranslatorInstances(entry.getKey(), entry.getValue())
            ));

    // now translate the rendered content of the bound monitors and turn them into DTOs
    return boundMonitors.stream()
        .map(boundMonitor -> {
          try {
            return translateOne(
                boundMonitor,
                operatorsByAgentType.get(boundMonitor.getMonitor().getAgentType())
            );
          } catch (MonitorContentTranslationException e) {
            log.error("Failed to translate boundMonitor={}", boundMonitor, e);
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private BoundMonitorDTO translateOne(BoundMonitor boundMonitor,
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
      if (operator.getMonitorType() != null &&
          !operator.getMonitorType().equals(boundMonitor.getMonitor().getMonitorType())) {
        continue;
      }

      if (operator.getSelectorScope() != null &&
          !operator.getSelectorScope().equals(boundMonitor.getMonitor().getSelectorScope())) {
        continue;
      }

      final Class<? extends MonitorTranslatorSpec> specClass = operator.getTranslatorSpec().getClass();

      final MonitorTranslator<? extends MonitorTranslatorSpec> translator =
          specToImpl.get(specClass);

      if (translator != null) {
        translator.translate(operator.getTranslatorSpec(), contentTree);
      } else {
        throw new MonitorContentTranslationException(
            String.format("Missing translator for specification type %s", specClass));
      }
    }

    if (!contentTree.hasNonNull(MonitorTranslatorSpec.TYPE_PROPERTY)) {
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
  private List<MonitorTranslationOperator> loadTranslatorInstances(AgentType agentType, String agentVersion) {
    final Version agentSemVer = Version.valueOf(agentVersion);

    return
        monitorTranslationOperatorRepository.findAllByAgentType(agentType).stream()
            .sorted(MonitorContentTranslationService::highestPrecedenceFirst)
            .filter(op ->
                // match any
                op.getAgentVersions() == null ||
                    // or match within range
                    agentSemVer.satisfies(op.getAgentVersions())
            )
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
      // when both have versions, resort to a reverse textual comparison trying to put something
      // like ">=1.12" ahead of ">=1.11"
      return rhs.getAgentVersions().compareTo(lhs.getAgentVersions());
    }
  }
}
