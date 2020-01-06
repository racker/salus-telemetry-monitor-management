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

package com.rackspace.salus.monitor_management.web.model.validator;

import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import java.util.Map;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class ValidCreateMonitorValidator implements ConstraintValidator<ValidCreateMonitor, DetailedMonitorInput> {
   public void initialize(ValidCreateMonitor constraint) {
   }

   public boolean isValid(DetailedMonitorInput monitorInput, ConstraintValidatorContext context) {
      Map<String, String> labelSelector = monitorInput.getLabelSelector();
      String resourceId = monitorInput.getResourceId();
      if (ValidUpdateMonitorValidator.bothResourceAndLabelsSet(monitorInput)) {
         return false;
      }
      if (ValidUpdateMonitorValidator.bothResourceAndExcludedSet(monitorInput)) {
         return false;
      }
      // Valid if either resourceId or labelSelector exists
      return (labelSelector != null || StringUtils.isNotBlank(resourceId));
   }

}
