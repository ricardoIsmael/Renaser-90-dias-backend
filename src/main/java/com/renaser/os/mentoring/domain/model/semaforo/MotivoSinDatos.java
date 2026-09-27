package com.renaser.os.mentoring.domain.model.semaforo;

import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;

import java.util.Collection;

/**
 * Por qué un aprendiz está «Sin datos» en una vista de grupo (S-5 de
 * docs/specs/RETROALIMENTACION_2026-09-26.md): el mentor necesita distinguir a quien no arrancó de
 * quien está en el programa sin nada planificado y de quien ya está fuera de él. Es un campo
 * CALCULADO de los días que ya trae la ventana: no se guarda en ningún lado y no cambia el color,
 * el porcentaje ni la regla del semáforo (decisión del dueño del 2026-09-26: la regla queda como
 * está).
 *
 * <p>El orden de las constantes es el de urgencia dentro de «Sin datos» ({@link OrdenDelSemaforo}):
 * primero quien está en el programa y no planificó nada —el más desconectado—, al final quien ya no
 * se mide por calendario.
 */
public enum MotivoSinDatos {

    /** Tuvo días del programa en la ventana y en ninguno había hábitos ni objetivos programados. */
    SIN_NADA_PLANIFICADO,
    /** No activó su programa: no arrancó. El semáforo no lo mide. */
    NO_ACTIVADO,
    /** Sus días del programa en la ventana todavía no los calculó el barrido horario. */
    PENDIENTE_DE_CALCULO,
    /** Staff con programa propio que pausó su semáforo toda la ventana. */
    PAUSADO,
    /** La ventana entera cae antes de su día 1 (día 0) o después de su día 90 (graduado). */
    FUERA_DEL_PROGRAMA;

    /**
     * El motivo que explican los días de una ventana sin ningún día medido. Si hay mezcla, gana el
     * que más dice de la persona: un día del programa sin nada programado pesa más que uno pendiente,
     * pausado o fuera del programa.
     */
    public static MotivoSinDatos de(Collection<DiaDelSemaforo> dias) {
        if (hayDia(dias, EstadoDiaSemaforo.SIN_DATOS)) {
            return SIN_NADA_PLANIFICADO;
        }
        if (hayDia(dias, EstadoDiaSemaforo.PENDIENTE)) {
            return PENDIENTE_DE_CALCULO;
        }
        if (hayDia(dias, EstadoDiaSemaforo.PAUSADO)) {
            return PAUSADO;
        }
        return FUERA_DEL_PROGRAMA;
    }

    private static boolean hayDia(Collection<DiaDelSemaforo> dias, EstadoDiaSemaforo estado) {
        return dias.stream().anyMatch(dia -> dia.estado() == estado);
    }
}
