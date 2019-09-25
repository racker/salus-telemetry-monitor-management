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

package com.rackspace.salus.monitor_management.translators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rackspace.salus.monitor_management.web.model.translators.MonitorTranslatorSpec;
import com.rackspace.salus.monitor_management.web.model.translators.RenameFieldSpec;
import org.springframework.stereotype.Component;

@Component
public class RenameFieldTranslator implements MonitorTranslator<RenameFieldSpec> {

  @Override
  public void translate(MonitorTranslatorSpec spec, ObjectNode contentTree) {
    final RenameFieldSpec ourSpec = (RenameFieldSpec) spec;

    final JsonNode node = contentTree.remove(ourSpec.getFrom());
    if (node != null) {
      contentTree.set(ourSpec.getTo(), node);
    }
  }

}
