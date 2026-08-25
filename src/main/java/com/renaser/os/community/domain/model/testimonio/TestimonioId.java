package com.renaser.os.community.domain.model.testimonio;

import java.util.UUID;

/** Identidad de un testimonio (tabla `testimonios`). */
public record TestimonioId(UUID value) {

    public TestimonioId {
        if (value == null) {
            throw new IllegalArgumentException("TestimonioId no puede ser null");
        }
    }

    public static TestimonioId of(UUID value) {
        return new TestimonioId(value);
    }

    public static TestimonioId newId() {
        return new TestimonioId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
