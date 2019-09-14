package com.rackspace.salus.monitor_management.web.validator;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.rackspace.salus.monitor_management.web.model.telegraf.PostgresqlRemote;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

public class PostgresqlRemoteValidator implements ConstraintValidator <PostgresqlRemoteValidator.AtMostOneOf, PostgresqlRemote> {

    @Override
    public boolean isValid(PostgresqlRemote monitor, ConstraintValidatorContext context) {
        int count = 0;

        if (!(monitor.getDatabases() == null)) {
            count++;
        }
        if (!(monitor.getIgnoredDatabases() == null)) {
            count++;
        }
        if(count > 1) {
            return false;
        } else {
            return true;
        }
    }

    @Target({TYPE, ANNOTATION_TYPE}) // class level constraint
    @Retention(RUNTIME)
    @Constraint(validatedBy = PostgresqlRemoteValidator.class) // validator
    @Documented
    public @interface AtMostOneOf {
        String DEFAULT_MESSAGE = "at most one of the 'databases' and 'ignored_databases' properties must be set";
        String message() default DEFAULT_MESSAGE;

        Class<?>[] groups() default {}; // required

        Class<? extends Payload>[] payload() default {}; // required
    }
}
