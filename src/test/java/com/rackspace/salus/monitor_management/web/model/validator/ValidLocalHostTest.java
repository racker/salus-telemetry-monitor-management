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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class ValidLocalHostTest {
  private LocalValidatorFactoryBean validatorFactoryBean;

  @AllArgsConstructor
  static class WithLocalHost {
  @NotEmpty
  List<@ValidLocalHost String> servers;
  }

  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValid() {
    List<String> l = new ArrayList<>();
    l.add("tcp(127.0.0.1:3306)/");
    l.add("tcp(localhost:3306)/");

    final WithLocalHost obj = new WithLocalHost(l);

    final Set<ConstraintViolation<WithLocalHost>> results = validatorFactoryBean.validate(obj);

    assertThat(results, hasSize(0));
  }

  @Test
  public void testInvalid() {
    List<String> l = new ArrayList<>();
    l.add("tcp(127.0.0.1:3306)/");
    l.add("tcp(localhos:3306)/");
    final WithLocalHost obj = new WithLocalHost(l);

    final Set<ConstraintViolation<WithLocalHost>> results = validatorFactoryBean.validate(obj);

    assertThat(results, hasSize(1));
    assertThat(new ArrayList<>(results).get(0).getMessage(),
        containsString(ValidLocalHost.DEFAULT_MESSAGE));

  }

  @Test
  public void testEmpty() {
    List<String> l = new ArrayList<>();
    l.add("");
    final WithLocalHost obj = new WithLocalHost(l);

    final Set<ConstraintViolation<WithLocalHost>> results = validatorFactoryBean.validate(obj);

    assertThat(results, hasSize(1));
    assertThat(new ArrayList<>(results).get(0).getMessage(),
        containsString(ValidLocalHost.DEFAULT_MESSAGE));

  }

  @Test
  public void testNull() {
    List<String> l = new ArrayList<>();
    l.add(null);
    final WithLocalHost obj = new WithLocalHost(l);

    final Set<ConstraintViolation<WithLocalHost>> results = validatorFactoryBean.validate(obj);

    assertThat(results, hasSize(0));
  }
}
