package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.points.api.EstadoDiaSemaforo;

import java.util.Optional;

/**
 * Por qué un día de la ficha no tiene nada que medir, dicho en palabras (spec 005, FR-019): la falta de datos no es
 * incumplimiento y nunca se presenta como tal. Los nombres son los de {@code MotivoSinDatos} del semáforo, para que la
 * app los lea con las mismas palabras que ya usa en la tabla del grupo; {@link #NO_ACTIVADO} cubre a quien el semáforo
 * ni siquiera mide (todavía no activó su programa). {@link #EN_CURSO} es de hoy, que llega solo cuando se pide el
 * viernes de hoy: la ventana del resumen termina ayer y no tiene días por venir.
 */
public enum MotivoDelDia {
    SIN_NADA_PLANIFICADO,
    NO_ACTIVADO,
    PENDIENTE_DE_CALCULO,
    CUENTA_SUSPENDIDA,
    PAUSADO,
    FUERA_DEL_PROGRAMA,
    /** Hoy: el día corre y el semáforo solo mide los días cerrados. */
    EN_CURSO;

    /** Vacío si el día se midió: tiene porcentaje y no hace falta explicar nada. */
    public static Optional<MotivoDelDia> de(EstadoDiaSemaforo estado) {
        return switch (estado) {
            case MEDIDO -> Optional.empty();
            case SIN_DATOS -> Optional.of(SIN_NADA_PLANIFICADO);
            case PAUSADO -> Optional.of(PAUSADO);
            case CUENTA_SUSPENDIDA -> Optional.of(CUENTA_SUSPENDIDA);
            case PENDIENTE -> Optional.of(PENDIENTE_DE_CALCULO);
            case FUERA_DEL_PROGRAMA -> Optional.of(FUERA_DEL_PROGRAMA);
        };
    }
}
