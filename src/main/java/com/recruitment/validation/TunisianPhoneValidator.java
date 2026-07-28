package com.recruitment.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TunisianPhoneValidator implements ConstraintValidator<TunisianPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return PhoneValidator.isValid(value);
    }
}
