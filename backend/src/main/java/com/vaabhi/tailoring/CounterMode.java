package com.vaabhi.tailoring;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jspecify.annotations.Nullable;

/** A payment mode someone can enter at the counter: cash, UPI, card or bank. Online and COD come from web orders. */
@Documented
@Constraint(validatedBy = CounterMode.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CounterMode {

    String message() default "Pick cash, UPI, card or bank";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Null is valid; pair with {@code @NotNull} where a mode is required. */
    class Validator implements ConstraintValidator<CounterMode, @Nullable PaymentMode> {
        @Override
        public boolean isValid(@Nullable PaymentMode value, ConstraintValidatorContext context) {
            return value != PaymentMode.RAZORPAY && value != PaymentMode.COD;
        }
    }
}
