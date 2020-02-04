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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.MonitorTranslationOperator;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.repositories.MonitorTranslationOperatorRepository;
import com.rackspace.salus.telemetry.translators.MonitorTranslator;
import com.rackspace.salus.telemetry.translators.RenameFieldKeyTranslator;
import com.rackspace.salus.telemetry.translators.RenameTypeTranslator;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.boot.test.json.JsonContent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    MonitorContentTranslationService.class,
    MeterRegistryTestConfig.class
})
@AutoConfigureJson
public class MonitorContentTranslationService_Translations_Test {

  private static final String OLD_FIELD_VALUE = "value-1";

  @Autowired
  MonitorContentTranslationService service;

  @Autowired
  ObjectMapper objectMapper;

  @MockBean
  MonitorTranslationOperatorRepository repository;

  private BasicJsonTester json = new BasicJsonTester(getClass());

  @Test
  public void testTranslate_matchAnyVersion() throws JsonProcessingException {
    final List<MonitorTranslationOperator> operators =
        buildOperatorList(AgentType.TELEGRAF, null, "old_field");

    when(repository.findAllByAgentType(any()))
        .thenReturn(operators);

    final List<BoundMonitor> boundMonitors = buildBoundMonitors();

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "1.12.0"));

    // VERIFY

    assertThat(dtos).hasSize(1);
    final String renderedContent = dtos.get(0).getRenderedContent();
    assertRenameTranslation(renderedContent);

    verify(repository).findAllByAgentType(AgentType.TELEGRAF);
  }

  @Test
  public void testTranslate_versionRange_match() throws JsonProcessingException {
    final List<MonitorTranslationOperator> operators =
        buildOperatorList(AgentType.TELEGRAF, "[1.11.2,1.12.0)", "old_field");

    when(repository.findAllByAgentType(any()))
        .thenReturn(operators);

    final List<BoundMonitor> boundMonitors = buildBoundMonitors();

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "1.11.3"));

    // VERIFY

    assertThat(dtos).hasSize(1);
    final String renderedContent = dtos.get(0).getRenderedContent();
    assertRenameTranslation(renderedContent);

    verify(repository).findAllByAgentType(AgentType.TELEGRAF);
  }

  @Test
  public void testTranslate_versionRange_notMatch() throws JsonProcessingException {
    final List<MonitorTranslationOperator> operators =
        buildOperatorList(AgentType.TELEGRAF, "[1.11.2,1.12.0)", "old_field");

    when(repository.findAllByAgentType(any()))
        .thenReturn(operators);

    final List<BoundMonitor> boundMonitors = buildBoundMonitors();

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "2.0.0"));

    // VERIFY

    assertThat(dtos).hasSize(1);
    final String renderedContent = dtos.get(0).getRenderedContent();
    assertNoRenameTranslation(renderedContent);

    verify(repository).findAllByAgentType(AgentType.TELEGRAF);
  }

  @Test
  public void testTranslate_versionRange_notValid() throws JsonProcessingException {
    final List<MonitorTranslationOperator> operators =
        buildOperatorList(AgentType.TELEGRAF, "[1.0.0", "old_field");

    when(repository.findAllByAgentType(any()))
        .thenReturn(operators);

    final List<BoundMonitor> boundMonitors = buildBoundMonitors();

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "2.0.0"));

    // VERIFY

    assertThat(dtos).hasSize(1);
    final String renderedContent = dtos.get(0).getRenderedContent();
    // operator didn't match since it was invalid, so no translation should have occurred
    assertNoRenameTranslation(renderedContent);

    verify(repository).findAllByAgentType(AgentType.TELEGRAF);
  }

  @Test
  public void testTranslate_emptyAgentInstallsGiven() throws JsonProcessingException {
    final List<BoundMonitor> boundMonitors = buildBoundMonitors();
    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of());

    // VERIFY

    assertThat(dtos).isEmpty();

    verifyNoMoreInteractions(repository);
  }

  @Test
  public void testTranslate_nullAgentInstallsGiven() throws JsonProcessingException {
    final List<BoundMonitor> boundMonitors = buildBoundMonitors();

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, null);

    // VERIFY

    assertThat(dtos).isEmpty();

    verifyNoMoreInteractions(repository);
  }

  @Test
  public void testTranslate_noMatchingTranslations_byAgentType() throws JsonProcessingException {
    when(repository.findAllByAgentType(any()))
        .thenReturn(
            // simulate query with no matches
            List.of()
        );

    final List<BoundMonitor> boundMonitors = buildBoundMonitors();

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "1.11.3"));

    // VERIFY

    assertThat(dtos).hasSize(1);
    final String renderedContent = dtos.get(0).getRenderedContent();
    // content was left as is
    assertNoRenameTranslation(renderedContent);

    verify(repository).findAllByAgentType(AgentType.TELEGRAF);
  }

  @Test
  public void testTranslate_match_byMonitorType() throws JsonProcessingException {
    final List<MonitorTranslationOperator> operators = List.of(
        new MonitorTranslationOperator()
            .setName("match")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setTranslatorSpec(buildRenameFieldSpec("matches", "afterMatch"))
            // TEST matching monitor type
            .setMonitorType(MonitorType.cpu),
        new MonitorTranslationOperator()
            .setName("dontMatch")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setTranslatorSpec(buildRenameFieldSpec("dontMatch", "neverUsed"))
            // TEST mismatching monitor type
            .setMonitorType(MonitorType.mem)
    );

    when(repository.findAllByAgentType(any()))
        .thenReturn(operators);

    final List<BoundMonitor> boundMonitors = List.of(new BoundMonitor()
        .setMonitor(
            new Monitor()
                .setContent("not used")
                .setAgentType(AgentType.TELEGRAF)
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setMonitorType(MonitorType.cpu)
        )
        .setRenderedContent(buildRenderedContent("cpu",
            "matches", "value-matches",
            "dontMatch", "value-dontMatch"))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH));

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "1.11.3"));

    assertThat(dtos).hasSize(1);

    final JsonContent<Object> jsonContent = json.from(dtos.get(0).getRenderedContent());
    assertThat(jsonContent).hasJsonPathValue("$.type", "cpu");
    assertThat(jsonContent).hasJsonPathValue("$.afterMatch", "value-matches");
    assertThat(jsonContent).hasJsonPathValue("$.dontMatch", "value-dontMatch");
    assertThat(jsonContent).hasEmptyJsonPathValue("$.neverUsed");
  }

  @Test
  public void testTranslate_match_byMonitorType_afterRename() throws JsonProcessingException {
    final List<MonitorTranslationOperator> operators = List.of(
        new MonitorTranslationOperator()
            .setName("renameType")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setTranslatorSpec(buildRenameTypeSpec("newType"))
            // TEST type is renamed
            .setMonitorType(MonitorType.cpu),
        new MonitorTranslationOperator()
            .setName("renameField")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setTranslatorSpec(buildRenameFieldSpec("beforeTranslate", "afterTranslate"))
            // TEST translation still matches when original type has been altered
            .setMonitorType(MonitorType.cpu)
    );

    when(repository.findAllByAgentType(any()))
        .thenReturn(operators);

    final List<BoundMonitor> boundMonitors = List.of(new BoundMonitor()
        .setMonitor(
            new Monitor()
                .setContent("not used")
                .setAgentType(AgentType.TELEGRAF)
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setMonitorType(MonitorType.cpu)
        )
        .setRenderedContent(buildRenderedContent("cpu",
            "beforeTranslate", "value-matches"))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH));

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "1.11.3"));

    assertThat(dtos).hasSize(1);

    final JsonContent<Object> jsonContent = json.from(dtos.get(0).getRenderedContent());
    assertThat(jsonContent).hasJsonPathValue("$.type", "newType");
    assertThat(jsonContent).hasJsonPathValue("$.afterTranslate", "value-matches");
    assertThat(jsonContent).hasEmptyJsonPathValue("$.beforeTranslate");
    assertThat(jsonContent).hasEmptyJsonPathValue("$.neverUsed");
  }

  @Test
  public void testTranslate_match_bySelectorScope() throws JsonProcessingException {
    final List<MonitorTranslationOperator> operators = List.of(
        new MonitorTranslationOperator()
            .setName("match")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setTranslatorSpec(buildRenameFieldSpec("matches", "afterMatch"))
            // TEST matching selector scope
            .setSelectorScope(ConfigSelectorScope.LOCAL),
        new MonitorTranslationOperator()
            .setName("dontMatch")
            .setAgentType(AgentType.TELEGRAF)
            .setAgentVersions(null)
            .setTranslatorSpec(buildRenameFieldSpec("dontMatch", "neverUsed"))
            // TEST mismatching scope
            .setSelectorScope(ConfigSelectorScope.REMOTE)
    );

    when(repository.findAllByAgentType(any()))
        .thenReturn(operators);

    final List<BoundMonitor> boundMonitors = List.of(new BoundMonitor()
        .setMonitor(
            new Monitor()
                .setContent("not used")
                .setAgentType(AgentType.TELEGRAF)
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setMonitorType(MonitorType.mysql)
        )
        .setRenderedContent(buildRenderedContent("mysql",
            "matches", "value-matches",
            "dontMatch", "value-dontMatch"))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH));

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "1.11.3"));

    assertThat(dtos).hasSize(1);

    final JsonContent<Object> jsonContent = json.from(dtos.get(0).getRenderedContent());
    assertThat(jsonContent).hasJsonPathValue("$.type", "mysql");
    assertThat(jsonContent).hasJsonPathValue("$.afterMatch", "value-matches");
    assertThat(jsonContent).hasJsonPathValue("$.dontMatch", "value-dontMatch");
    assertThat(jsonContent).hasEmptyJsonPathValue("$.neverUsed");
  }

  @Test
  public void testTranslate_translatedTypePropertyMissing() throws JsonProcessingException {
    final List<MonitorTranslationOperator> operators =
        buildOperatorList(AgentType.TELEGRAF, null,
            // TEST abuse rename translator to rename the JSON type field
            "type"
        );

    when(repository.findAllByAgentType(any()))
        .thenReturn(operators);

    final List<BoundMonitor> boundMonitors = buildBoundMonitors();

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "1.12.0"));

    // VERIFY

    // all (of one) bound monitors get pruned due to translator mangling the result
    assertThat(dtos).isEmpty();

    verify(repository).findAllByAgentType(AgentType.TELEGRAF);
  }

  @Test
  public void testTranslate_invalidRenderedContent() {
    final List<MonitorTranslationOperator> operators =
        buildOperatorList(AgentType.TELEGRAF, null, "old_field");

    when(repository.findAllByAgentType(any()))
        .thenReturn(operators);

    final List<BoundMonitor> boundMonitors = List.of(new BoundMonitor()
        .setMonitor(
            new Monitor()
                .setContent("not used")
                .setAgentType(AgentType.TELEGRAF)
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setMonitorType(MonitorType.cpu)
        )
        .setRenderedContent("this is not valid json")
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH));

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "1.12.0"));

    // VERIFY

    assertThat(dtos).isEmpty();

    verify(repository).findAllByAgentType(AgentType.TELEGRAF);
  }

  @Test
  public void testTranslate_nonSemVer() throws JsonProcessingException {
    when(repository.findAllByAgentType(any()))
        .thenReturn(
            // simulate query with no matches
            List.of()
        );

    final List<BoundMonitor> boundMonitors = buildBoundMonitors();

    // EXECUTE

    final List<BoundMonitorDTO> dtos =
        service.translate(boundMonitors, Map.of(AgentType.TELEGRAF, "0.1"));

    // VERIFY

    assertThat(dtos).hasSize(1);
    final String renderedContent = dtos.get(0).getRenderedContent();
    // content was left as is
    assertNoRenameTranslation(renderedContent);

    verify(repository).findAllByAgentType(AgentType.TELEGRAF);
  }

  private void assertRenameTranslation(String renderedContent) {
    assertThat(json.from(renderedContent)).hasJsonPathValue("$.type", "cpu");
    assertThat(json.from(renderedContent)).hasJsonPathValue("$.new_field", OLD_FIELD_VALUE);
    assertThat(json.from(renderedContent)).hasEmptyJsonPathValue("$.old_field");
  }

  private void assertNoRenameTranslation(String renderedContent) {
    assertThat(json.from(renderedContent)).hasJsonPathValue("$.type", "cpu");
    assertThat(json.from(renderedContent)).hasJsonPathValue("$.old_field", OLD_FIELD_VALUE);
    assertThat(json.from(renderedContent)).hasEmptyJsonPathValue("$.new_field");
  }

  private List<BoundMonitor> buildBoundMonitors() throws JsonProcessingException {
    return List.of(new BoundMonitor()
        .setMonitor(
            new Monitor()
                .setContent("not used")
                .setAgentType(AgentType.TELEGRAF)
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setMonitorType(MonitorType.cpu)
        )
        .setRenderedContent(buildRenderedContent("cpu", "old_field", OLD_FIELD_VALUE))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH));
  }

  private List<MonitorTranslationOperator> buildOperatorList(AgentType agentType,
                                                             String agentVersions,
                                                             String renameFromField) {
    return List.of(
        new MonitorTranslationOperator()
            .setName("rename-"+renameFromField)
            .setAgentType(agentType)
            .setAgentVersions(agentVersions)
            .setTranslatorSpec(buildRenameFieldSpec(renameFromField, "new_field"))
    );
  }

  private MonitorTranslator buildRenameFieldSpec(String from, String to) {
    return new RenameFieldKeyTranslator().setFrom(from).setTo(to);
  }

  private MonitorTranslator buildRenameTypeSpec(String to) {
    return new RenameTypeTranslator().setValue(to);
  }

  private String buildRenderedContent(String type, String... fieldAndValue)
      throws JsonProcessingException {
    final HashMap<String, String> fields = new HashMap<>();
    fields.put("type", type);
    for (int i = 0; i < fieldAndValue.length; i += 2) {
      fields.put(fieldAndValue[i], fieldAndValue[i + 1]);
    }

    return objectMapper.writeValueAsString(fields);
  }

}