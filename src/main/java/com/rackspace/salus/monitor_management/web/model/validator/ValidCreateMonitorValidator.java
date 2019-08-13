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

package com.rackspace.salus.monitor_management.web.model.validator;

import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import java.util.Map;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ValidCreateMonitorValidator implements ConstraintValidator<ValidCreateMonitor, DetailedMonitorInput> {
   public void initialize(ValidCreateMonitor constraint) {
   }

   public boolean isValid(DetailedMonitorInput monitorInput, ConstraintValidatorContext context) {
      Map<String, String > labelSelector = monitorInput.getLabelSelector();
      String resourceId = monitorInput.getResourceId();
      if (resourceId != null && !resourceId.equals("")) {
         if (labelSelector != null && labelSelector.size() > 0) {
            return false;
         }
      }
      if (labelSelector == null) {
         if (resourceId == null || resourceId.equals("")) {
            return false;
         }
      }
      return true;
   }

}
