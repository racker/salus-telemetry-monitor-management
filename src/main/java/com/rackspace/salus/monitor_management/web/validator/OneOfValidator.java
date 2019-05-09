package com.rackspace.salus.monitor_management.web.validator;

import com.rackspace.salus.monitor_management.web.model.telegraf.Procstat;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class OneOfValidator implements ConstraintValidator <OneOfValidator.OneOf, Procstat> {

    @Override
    public boolean isValid(Procstat monitor, ConstraintValidatorContext context) {
        String pidfile;
        String user;
        String exe;
        String pattern;
        String systemd_unit;
        String cgroup;
        String win_service;

        return false;
    }

    @Target({TYPE, ANNOTATION_TYPE}) // class level constraint
    @Retention(RUNTIME)
    @Constraint(validatedBy = OneOfValidator.class) // validator
    @Documented
    public @interface OneOf {
        String message() default "one of the properties needs to be set"; // default error message

        Class<?>[] groups() default {}; // required

        Class<? extends Payload>[] payload() default {}; // required
    }
}
