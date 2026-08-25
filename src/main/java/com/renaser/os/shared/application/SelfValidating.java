package com.renaser.os.shared.application;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.lang.reflect.Constructor;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SelfValidating {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private SelfValidating() {
    }

    @SuppressWarnings("unchecked")
    public static <T> void validateConstructorArgs(Class<T> recordClass, Object... args) {
        Constructor<T> constructor = (Constructor<T>) recordClass.getDeclaredConstructors()[0];
        Set<ConstraintViolation<T>> violations = VALIDATOR.forExecutables()
                .validateConstructorParameters(constructor, args);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(new LinkedHashSet<>(violations));
        }
    }
}
