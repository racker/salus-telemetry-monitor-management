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

package com.rackspace.salus.monitor_management.web.model.translators;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@JsonTest
public class RenameFieldTranslatorTest {

  @Autowired
  ObjectMapper objectMapper;

  private BasicJsonTester jsonTester = new BasicJsonTester(getClass());

  @Test
  public void testTranslate() {
    final Map<String,String> content = Map.of(
        "field-match", "value-match",
        "field-not-match", "value-not-match"
    );

    final RenameFieldTranslator translator = new RenameFieldTranslator()
        .setFrom("field-match")
        .setTo("new-field");

    final ObjectNode contentTree = objectMapper.valueToTree(content);

    translator.translate(contentTree);

    assertThat(contentTree.get("new-field")).isNotNull();
    assertThat(contentTree.get("new-field").asText()).isEqualTo("value-match");

    assertThat(contentTree.get("field-match")).isNull();

    assertThat(contentTree.get("field-not-match")).isNotNull();
    assertThat(contentTree.get("field-not-match").asText()).isEqualTo("value-not-match");
  }
}