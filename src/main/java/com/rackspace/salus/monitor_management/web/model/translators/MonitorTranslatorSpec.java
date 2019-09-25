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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonTypeInfo(use = Id.NAME, property = MonitorTranslatorSpec.TYPE_PROPERTY)
@JsonSubTypes({
    @Type(name = "renameField", value = RenameFieldSpec.class)
})
public abstract class MonitorTranslatorSpec {

  public static final String TYPE_PROPERTY = "type";

  /**
   * Translate the given monitor content tree for the monitor of the given type.
   * @param contentTree can be manipulated in place, if this translator finds it is applicable
   */
  public abstract void translate(ObjectNode contentTree);

}
