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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class ValidHostAndPortValidatorTest {
  private LocalValidatorFactoryBean validatorFactoryBean;

  @AllArgsConstructor
  static class WithOptionalAddress {
    @ValidHostAndPort
    String address;
  }

  @AllArgsConstructor
  static class WithRequiredAddress {
    @NotNull
    @ValidHostAndPort
    String address;
  }

  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValidation_valid_ip() {
    final WithRequiredAddress obj = new WithRequiredAddress("127.0.0.1:22");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(0));
  }

  @Test
  public void testValidation_valid_host() {
    final WithRequiredAddress obj = new WithRequiredAddress("localhost:22");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(0));
  }

  @Test
  public void testValidation_valid_domain() {
    final WithRequiredAddress obj = new WithRequiredAddress("example.com:22");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(0));
  }

  @Test
  public void testValidation_valid_optional() {
    final WithOptionalAddress obj = new WithOptionalAddress(null);

    final Set<ConstraintViolation<WithOptionalAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(0));
  }

  @Test
  public void testValidation_missingPort() {
    final WithRequiredAddress obj = new WithRequiredAddress("localhost");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(1));
    assertThat(
        result.iterator().next().getConstraintDescriptor().getAnnotation().annotationType(),
        equalTo(ValidHostAndPort.class)
    );
  }

  @Test
  public void testValidation_invalidIp() {
    final WithRequiredAddress obj = new WithRequiredAddress("127.3:8080");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(1));
    assertThat(
        result.iterator().next().getConstraintDescriptor().getAnnotation().annotationType(),
        equalTo(ValidHostAndPort.class)
    );
  }

  @Test
  public void testValidation_invalidPort() {
    final WithRequiredAddress obj = new WithRequiredAddress("localhost:notAPort");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(1));
    assertThat(
        result.iterator().next().getConstraintDescriptor().getAnnotation().annotationType(),
        equalTo(ValidHostAndPort.class)
    );
  }

  @Test
  public void testValidation_invalidDomain() {
    final WithRequiredAddress obj = new WithRequiredAddress("not$valid:80");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(1));
    assertThat(
        result.iterator().next().getConstraintDescriptor().getAnnotation().annotationType(),
        equalTo(ValidHostAndPort.class)
    );
  }

  @Test
  public void testValidation_validMetadata() {
    final WithRequiredAddress obj = new WithRequiredAddress("${resource.metadata.ping_ip}");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(0));
  }

  @Test
  public void testValidation_invalidMetadata() {
    final WithRequiredAddress obj = new WithRequiredAddress("start${resource.metadata.ping_ip}end");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(1));
    assertThat(
        result.iterator().next().getConstraintDescriptor().getAnnotation().annotationType(),
        equalTo(ValidHostAndPort.class)
    );
  }

  @Test
  public void testValidation_invalidMetadata_notClosed() {
    final WithRequiredAddress obj = new WithRequiredAddress("${resource.metadata.ping_ip");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(1));
    assertThat(
        result.iterator().next().getConstraintDescriptor().getAnnotation().annotationType(),
        equalTo(ValidHostAndPort.class)
    );
  }

  @Test
  public void testValidation_invalidMetadata_missingDollar() {
    final WithRequiredAddress obj = new WithRequiredAddress("{resource.metadata.ping_ip}");

    final Set<ConstraintViolation<WithRequiredAddress>> result = validatorFactoryBean
        .validate(obj);

    assertThat(result, hasSize(1));
    assertThat(
        result.iterator().next().getConstraintDescriptor().getAnnotation().annotationType(),
        equalTo(ValidHostAndPort.class)
    );
  }
}