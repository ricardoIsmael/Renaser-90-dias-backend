package com.renaser.os.calendar.domain.model.evento.audiencia;

import java.util.Objects;
import java.util.UUID;

/**
 * Una parte de la audiencia tal como la pide quien crea el evento (spec 044, contrato §1). Cada tipo exige lo suyo y nada más: una persona su id, un
 * grupo el suyo, un color el color; el personal puede llevar una persona o ninguna (todo el personal).
 *
 * @param id     la persona ({@link TipoDeParte#PERSONA}, {@link TipoDeParte#PERSONAL}) o el grupo ({@link TipoDeParte#GRUPO})
 * @param color  solo con {@link TipoDeParte#COLOR}
 */
public record ParteDeAudiencia(TipoDeParte tipo, UUID id, ColorDeAudiencia color) {

    public ParteDeAudiencia {
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        switch (tipo) {
            case PERSONA, GRUPO -> exigir(id != null && color == null, "La parte " + tipo + " necesita un id y no lleva color");
            case COLOR -> exigir(color != null && id == null, "La parte COLOR necesita un color y no lleva id");
            case PERSONAL -> exigir(color == null, "La parte PERSONAL no lleva color");
            case MIS_ALUMNOS, MENTORES -> exigir(id == null && color == null, "La parte " + tipo + " no lleva id ni color");
        }
    }

    public static ParteDeAudiencia persona(UUID id) {
        return new ParteDeAudiencia(TipoDeParte.PERSONA, id, null);
    }

    public static ParteDeAudiencia color(ColorDeAudiencia color) {
        return new ParteDeAudiencia(TipoDeParte.COLOR, null, color);
    }

    public static ParteDeAudiencia sola(TipoDeParte tipo) {
        return new ParteDeAudiencia(tipo, null, null);
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new IllegalArgumentException(mensaje);
        }
    }
}
