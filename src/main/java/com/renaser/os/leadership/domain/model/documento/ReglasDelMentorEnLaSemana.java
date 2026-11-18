package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Foco;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Las tres cuentas del documento semanal de un mentor que no son de nadie más (spec 056, FR-036): la tendencia de un aprendiz contra la
 * semana anterior, sus días sin actividad y el foco de la semana del mentor.
 */
public final class ReglasDelMentorEnLaSemana {

    private ReglasDelMentorEnLaSemana() {
    }

    /**
     * @param actual   el porcentaje de la semana; null si no se midió
     * @param anterior el de la semana anterior; null si no se midió
     * @return {@code SUBE}, {@code BAJA} o {@code IGUAL}; {@code SIN_COMPARACION} si alguna de las dos semanas no se midió: no se inventa un cero
     */
    public static String tendencia(BigDecimal actual, BigDecimal anterior) {
        if (actual == null || anterior == null) {
            return "SIN_COMPARACION";
        }
        int comparacion = actual.compareTo(anterior);
        return comparacion > 0 ? "SUBE" : comparacion < 0 ? "BAJA" : "IGUAL";
    }

    /**
     * Los días medidos de la semana en que no cumplió ningún hábito ni objetivo. Un día sin nada programado, pausado o sin calcular no cuenta: no
     * hay actividad que esperar de él.
     */
    public static int diasSinActividad(List<DiaDelSemaforo> dias) {
        return (int) dias.stream()
                .filter(dia -> dia.estado() == EstadoDiaSemaforo.MEDIDO)
                .filter(dia -> dia.habitosCumplidos() == 0 && dia.objetivosCumplidos() == 0)
                .count();
    }

    /** Lo más urgente primero: los aprendices en rojo, las consultas sin responder, las alertas; si no hay nada, mantener el ritmo. */
    public static Foco foco(int enRojo, int consultasSinResponder, int alertas) {
        if (enRojo > 0) {
            return new Foco("ESCRIBIR_A_LOS_ROJOS", enRojo);
        }
        if (consultasSinResponder > 0) {
            return new Foco("RESPONDER_CONSULTAS", consultasSinResponder);
        }
        return alertas > 0 ? new Foco("REVISAR_ALERTAS", alertas) : new Foco("MANTENER_EL_RITMO", 0);
    }
}
