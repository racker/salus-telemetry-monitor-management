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

package com.rackspace.salus.monitor_management.web.model;

import javax.validation.constraints.NotNull;
import javax.validation.groups.Default;
import org.springframework.validation.annotation.Validated;

/**
 * This contains validation group designations to allow for selective validation of fields
 * such as {@link NotNull#groups()} and then activated during operations such as
 * {@link Validated#value()}.
 */
public class ValidationGroups {

  /**
   * Used for validations that are activated during create operations
   */
  public interface Create extends Default { }
  public interface Update extends Default { }

}
