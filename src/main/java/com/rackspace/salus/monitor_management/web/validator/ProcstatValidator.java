package com.rackspace.salus.monitor_management.web.validator;

import com.rackspace.salus.monitor_management.web.model.telegraf.Procstat;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class ProcstatValidator implements ConstraintValidator <ProcstatValidator.OneOf, Procstat> {

    @Override
    public boolean isValid(Procstat monitor, ConstraintValidatorContext context) {
        int count = 0;

        if(!isNullOrEmpty(monitor.getPidFile())) {
            count++;
        }
        if(!isNullOrEmpty(monitor.getUser())) {
            count++;
        }
        if(!isNullOrEmpty(monitor.getExe())) {
            count++;
        }
        if(!isNullOrEmpty(monitor.getPattern())) {
            count++;
        }
        if(!isNullOrEmpty(monitor.getSystemd_unit())) {
            count++;
        }
        if(!isNullOrEmpty(monitor.getCgroup())) {
            count++;
        }
        if(!isNullOrEmpty(monitor.getWin_service())) {
            count++;
        }
        if(count != 1) {
            return false;
        } else {
            return true;
        }
    }

    @Target({TYPE, ANNOTATION_TYPE}) // class level constraint
    @Retention(RUNTIME)
    @Constraint(validatedBy = ProcstatValidator.class) // validator
    @Documented
    public @interface OneOf {
        String message() default "only one of the properties must be set"; // default error message

        Class<?>[] groups() default {}; // required

        Class<? extends Payload>[] payload() default {}; // required
    }
}
