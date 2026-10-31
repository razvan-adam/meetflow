package com.meetflow.identity.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * At most this many bytes in UTF-8. bcrypt only uses a password's first 72 bytes, and Spring
 * Security rejects longer ones, so a character limit alone isn't enough for non-ASCII passwords.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxBytes.Validator.class)
@interface MaxBytes {

  int value();

  String message() default "must be at most {value} bytes long";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<MaxBytes, String> {

    private int max;

    @Override
    public void initialize(MaxBytes constraint) {
      max = constraint.value();
    }

    @Override
    public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
      return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
  }
}
