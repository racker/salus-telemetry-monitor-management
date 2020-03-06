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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.monitor_management.web.validator.ValidGoDuration;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class ValidGoDurationTest {
  private LocalValidatorFactoryBean validatorFactoryBean;

  @AllArgsConstructor
  static class WithDuration {
    @ValidGoDuration
    String duration;
  }

  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValid() {
    final WithDuration obj = new WithDuration("5s");

    final Set<ConstraintViolation<WithDuration>> results = validatorFactoryBean.validate(obj);

    assertThat(results, hasSize(0));
  }

  @Test
  public void testInvalid_wrongUnits() {
    final WithDuration obj = new WithDuration("5d");

    final Set<ConstraintViolation<WithDuration>> results = validatorFactoryBean.validate(obj);

    assertInvalidDuration(results);
  }

  @Test
  public void testInvalid_noDigits() {
    final WithDuration obj = new WithDuration("m");

    final Set<ConstraintViolation<WithDuration>> results = validatorFactoryBean.validate(obj);

    assertInvalidDuration(results);
  }

  @Test
  public void testInvalid_empty() {
    final WithDuration obj = new WithDuration("");

    final Set<ConstraintViolation<WithDuration>> results = validatorFactoryBean.validate(obj);

    assertInvalidDuration(results);
  }

  @Test
  public void testInvalid_noUnits() {
    final WithDuration obj = new WithDuration("12");

    final Set<ConstraintViolation<WithDuration>> results = validatorFactoryBean.validate(obj);

    assertInvalidDuration(results);
  }

  private void assertInvalidDuration(Set<ConstraintViolation<WithDuration>> results) {
    assertThat(results, hasSize(1));
    final ConstraintViolation<WithDuration> violation = results.iterator().next();
    assertThat(
        violation.getConstraintDescriptor().getAnnotation().annotationType(),
        // since it's composite, the constituent constraint ends up here
        equalTo(Pattern.class)
    );
    assertThat(violation.getMessage(), equalTo("invalid duration"));
  }
}
