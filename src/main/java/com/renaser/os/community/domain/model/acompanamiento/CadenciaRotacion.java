package com.renaser.os.community.domain.model.acompanamiento;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Cada cuánto rota el mentor. D-02 confirmó mensual con opción semanal; P-02 fija el anclaje:
 * el día 1 del mes y el lunes, en la zona de la cohorte.
 *
 * <p>Anclar a una fecha del calendario y no a "30 días desde cada asignación" es deliberado:
 * con el segundo criterio cada grupo rotaría un día distinto y la evaluación mensual dejaría
 * de coincidir con el mes que el mentor ve en pantalla.
 */
public enum CadenciaRotacion {

    MENSUAL {
        @Override
        public LocalDate proximaFechaDespuesDe(LocalDate fechaLocal) {
            return fechaLocal.with(TemporalAdjusters.firstDayOfNextMonth());
        }
    },

    SEMANAL {
        @Override
        public LocalDate proximaFechaDespuesDe(LocalDate fechaLocal) {
            return fechaLocal.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
    };

    /**
     * Siguiente fecha de anclaje estrictamente posterior a {@code fechaLocal}. Estricta a
     * propósito: si hoy es día de rotación y ya se procesó, la próxima no puede ser hoy otra
     * vez. El efecto real se registra al procesar, sin retrofechar (plan.md §5).
     */
    public abstract LocalDate proximaFechaDespuesDe(LocalDate fechaLocal);
}
