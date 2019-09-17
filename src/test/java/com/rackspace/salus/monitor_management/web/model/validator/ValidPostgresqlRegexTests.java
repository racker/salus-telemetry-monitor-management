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

import com.rackspace.salus.monitor_management.web.model.telegraf.Postgresql;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class ValidPostgresqlRegexTests {
  private LocalValidatorFactoryBean validatorFactoryBean;

  @AllArgsConstructor
  static class WithPostgresqlRegex {
  @NotEmpty
  List<@Pattern(regexp = Postgresql.REGEXP, message = Postgresql.ERR_MESSAGE) String> servers;
  }

  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValid() {
    List<String> l = new ArrayList<>();
    l.add("postgres://pqgotest:password@localhost/dbname");
    l.add("host=localhost user=pqotest password=pw sslmode=false dbname=app_production");
    l.add("host=localhost");
    final WithPostgresqlRegex obj = new WithPostgresqlRegex(l);

    final Set<ConstraintViolation<WithPostgresqlRegex>> results = validatorFactoryBean.validate(obj);

    assertThat(results, hasSize(0));
  }

  @Test
  public void testInvalid() {
    testInvalidRegex("ostgres://pqgotest:password@localhost/dbname");
    testInvalidRegex("postgres:/pqgotest:password@localhost/dbname");
    testInvalidRegex("=localhost user=pqotest password=pw sslmode=false dbname=app_production");
    testInvalidRegex("");
  }
  private void testInvalidRegex(String regex) {
    final WithPostgresqlRegex obj = new WithPostgresqlRegex(List.of(regex));

    final Set<ConstraintViolation<WithPostgresqlRegex>> results = validatorFactoryBean.validate(obj);
    assertThat(results, hasSize(1));
    assertThat(new ArrayList<>(results).get(0).getMessage(),
        containsString(Postgresql.ERR_MESSAGE));

  }
}
