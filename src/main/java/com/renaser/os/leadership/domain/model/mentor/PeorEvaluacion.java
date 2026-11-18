package com.renaser.os.leadership.domain.model.mentor;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * El mentor con la peor evaluación del mes, para «Por resolver» (spec 056, FR-002): la más baja de las calculadas, si está por debajo del
 * 60 % —el mismo umbral del reporte del mes—. Una evaluación sin muestra o sin historial no es una evaluación mala: no entra.
 */
public final class PeorEvaluacion {

    /** El umbral del reporte del mes (spec 056, Supuestos): por debajo de este porcentaje, el mentor se le muestra al líder. */
    public static final BigDecimal UMBRAL = new BigDecimal("60");

    private PeorEvaluacion() {
    }

    /** @param porcentaje la evaluación del mes; null cuando no se pudo calcular (sin muestra, sin historial o la fuente cayó) */
    public record MentorEvaluado(UUID id, String nombre, BigDecimal porcentaje) {

        public MentorEvaluado {
            Objects.requireNonNull(id, "id es obligatorio");
        }
    }

    /** Ante un empate, el de nombre primero: el resultado no cambia de una lectura a otra. */
    public static Optional<MentorEvaluado> de(Collection<MentorEvaluado> mentores) {
        return mentores.stream()
                .filter(mentor -> mentor.porcentaje() != null && mentor.porcentaje().compareTo(UMBRAL) < 0)
                .min(Comparator.comparing(MentorEvaluado::porcentaje)
                        .thenComparing(MentorEvaluado::nombre, Comparator.nullsLast(String::compareToIgnoreCase)));
    }
}
