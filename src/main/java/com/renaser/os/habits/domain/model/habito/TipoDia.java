package com.renaser.os.habits.domain.model.habito;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Espejo de `tipo_dia` (baseline SQL). DISCIPLINA/INTOXICACION derivan del dia
 * de programa (ciclos fijos); TODOS aplica cualquier dia; DOMINGO es especial. */
public enum TipoDia {
    DISCIPLINA,
    INTOXICACION,
    TODOS,
    DOMINGO;

    /**
     * DOMINGO por dia de calendario; DISCIPLINA en cualquier otro caso. INTOXICACION (ciclos
     * fijos del repo viejo) NO esta implementado en esta version — ver docs/MODULO_HABITS.md.
     * Regla pura: la comparten la generacion de registros y la lectura de horarios vigentes.
     */
    public static TipoDia delDia(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.SUNDAY ? DOMINGO : DISCIPLINA;
    }
}
