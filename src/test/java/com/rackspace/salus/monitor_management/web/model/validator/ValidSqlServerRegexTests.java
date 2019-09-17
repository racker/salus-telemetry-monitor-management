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

import com.rackspace.salus.monitor_management.web.model.telegraf.SqlServer;
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

public class ValidSqlServerRegexTests {
  private LocalValidatorFactoryBean validatorFactoryBean;

  @AllArgsConstructor
  static class WithSqlServerRegex {
  @NotEmpty
  List<@Pattern(regexp = SqlServer.REGEXP, message = SqlServer.ERR_MESSAGE) String> servers;
  }

  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValid() {
    List<String> l = new ArrayList<>();
    l.add("sqlserver://username:password@host/instance?param1=value&param2=value");
    l.add("Server=192.168.1.10;Port=1433;User Id=user;Password=pw;app name=telegraf;log=1;");
    l.add("Server=192.168.1.10");
    final WithSqlServerRegex obj = new WithSqlServerRegex(l);

    final Set<ConstraintViolation<WithSqlServerRegex>> results = validatorFactoryBean.validate(obj);

    assertThat(results, hasSize(0));
  }

  @Test
  public void testInvalid() {
    testInvalidRegex("qlserver://username:password@host/instance?param1=value&param2=value");
    testInvalidRegex("sqlserver:/username:password@host/instance?param1=value&param2=value");
    testInvalidRegex("=192.168.1.10;Port=1433;User Id=user;Password=pw;app name=telegraf;log=1;");
    testInvalidRegex("");
  }
  private void testInvalidRegex(String regex) {
    final WithSqlServerRegex obj = new WithSqlServerRegex(List.of(regex));

    final Set<ConstraintViolation<WithSqlServerRegex>> results = validatorFactoryBean.validate(obj);
    assertThat(results, hasSize(1));
    assertThat(new ArrayList<>(results).get(0).getMessage(),
        containsString(SqlServer.ERR_MESSAGE));

  }
}
