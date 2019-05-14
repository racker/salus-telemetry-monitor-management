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

import com.rackspace.salus.common.util.CIDRUtils;
import java.util.List;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates the annotated list only contains strings that are valid CIDR notation.
 * A null list is also valid.
 */
public class ValidCidrListValidator implements ConstraintValidator<ValidCidrList, List<String>> {
  public void initialize(ValidCidrList constraint) {}

  public boolean isValid(List<String> obj, ConstraintValidatorContext context) {
    if (obj == null) {
      return true;
    }

    return obj.stream().allMatch(this::isValidSubnet);
  }

  /**
   * Verify if a given string is valid cidr notation.
   * @param input The string to validate.
   * @return True if it's valid, otherwise false.
   */
  private boolean isValidSubnet(String input) {
    try {
      new CIDRUtils(input);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

}
