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

import com.rackspace.salus.monitor_management.web.model.telegraf.Mysql;
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

public class ValidDbConnectionRegexTests {
  private LocalValidatorFactoryBean validatorFactoryBean;

  @AllArgsConstructor
  static class WithMysqlRegex {
  @NotEmpty
  List<@Pattern(regexp = Mysql.REGEXP, message = Mysql.ERR_MESSAGE) String> servers;
  }

  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValid() {
    List<String> l = new ArrayList<>();
    l.add("username@tcp(address)/dbname?param=value&paramN=valueN");
    l.add("username:password@tcp(address)/dbname?param=value");
    l.add("tcp(address)/");
    l.add("tcp(address)/dbname?xyz=value");
    l.add("/dbname?xyz=value");
    l.add("/dbname");
    l.add("/");
    final WithMysqlRegex obj = new WithMysqlRegex(l);

    final Set<ConstraintViolation<WithMysqlRegex>> results = validatorFactoryBean.validate(obj);

    assertThat(results, hasSize(0));
  }

  @Test
  public void testInvalid() {
    testInvalidRegex("username:tcp(address)/dbname?param=value&paramN=valueN");
    testInvalidRegex("username:password@address/dbname?param=value");
    testInvalidRegex("tc(address)/dbname?xyz=value");
    testInvalidRegex("username@localhost:333/dbname");
    testInvalidRegex("/dbname?=value");
  }
  private void testInvalidRegex(String regex) {
    final WithMysqlRegex obj = new WithMysqlRegex(List.of(regex));

    final Set<ConstraintViolation<WithMysqlRegex>> results = validatorFactoryBean.validate(obj);
    assertThat(results, hasSize(1));
    assertThat(new ArrayList<>(results).get(0).getMessage(),
        containsString(Mysql.ERR_MESSAGE));

  }
}
