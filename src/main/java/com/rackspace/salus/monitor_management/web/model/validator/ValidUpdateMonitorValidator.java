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
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

@Slf4j
public class ValidUpdateMonitorValidator implements ConstraintValidator<ValidUpdateMonitor, DetailedMonitorInput> {
   public void initialize(ValidUpdateMonitor constraint) {
   }

   static boolean bothResourceAndLabelsSet(DetailedMonitorInput monitorInput) {
      Map<String, String > labelSelector = monitorInput.getLabelSelector();
      String resourceId = monitorInput.getResourceId();
      return StringUtils.isNotBlank(resourceId) && !CollectionUtils.isEmpty(labelSelector);
   }

   public boolean isValid(DetailedMonitorInput monitorInput, ConstraintValidatorContext context) {
      return !bothResourceAndLabelsSet(monitorInput);
   }

}
