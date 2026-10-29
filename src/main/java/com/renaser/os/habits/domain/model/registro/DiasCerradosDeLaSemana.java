package com.renaser.os.habits.domain.model.registro;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * D-288 (spec 014 «Training por hora») — los días que una persona puede volver a mirar hábito por hábito: los que ya
 * CERRARON de su semana en curso, de ayer hacia atrás hasta el lunes, y nunca antes de su Día 1.
 *
 * <p>El tope existe porque el pedido se puede ejecutar mil veces («cuidado con el endpoint»): una semana son a lo sumo
 * seis fechas, y las respuestas, que no cambian, se guardan en el teléfono. Hoy no entra (se mira con
 * {@code GET /habit-tracks/today}, que sí genera lo que falta) ni el futuro, y tampoco otra semana, que se consulta
 * en {@code Yo → Mis evidencias}.
 *
 * <p>{@code hoy} es la fecha de HOY en la zona de la PERSONA, nunca la del servidor (regla 02, E-91): con el reloj en
 * la madrugada UTC, en Lima todavía es el día anterior, y con él cambian «ayer» y «el lunes». Quien arma esto es quien
 * ya resolvió la zona; esta clase no lee el reloj.
 *
 * <p>Una persona sin Día 1 ({@code diaUno == null}) no tiene ningún día que mirar. La semana es de lunes a domingo, la
 * misma que muestra Training.
 */
public final class DiasCerradosDeLaSemana {

    private final LocalDate hoy;
    private final LocalDate lunes;
    private final LocalDate diaUno;

    private DiasCerradosDeLaSemana(LocalDate hoy, LocalDate diaUno) {
        this.hoy = hoy;
        this.lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        this.diaUno = diaUno;
    }

    /**
     * @param hoy   la fecha de hoy en la zona del participante
     * @param diaUno {@code participantes_programa.fecha_inicio}, o {@code null} si todavía no eligió su Día 1
     */
    public static DiasCerradosDeLaSemana de(LocalDate hoy, LocalDate diaUno) {
        if (hoy == null) {
            throw new IllegalArgumentException("Hace falta la fecha de hoy en la zona de la persona");
        }
        return new DiasCerradosDeLaSemana(hoy, diaUno);
    }

    /** @throws IllegalArgumentException (400) si {@code fecha} no es un día cerrado de esta semana desde el Día 1 */
    public void exigirQueIncluya(LocalDate fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException("Falta la fecha del día que quieres ver.");
        }
        if (!fecha.isBefore(hoy)) {
            throw new IllegalArgumentException("Ese día todavía no cerró. Solo puedes ver los días que ya cerraron.");
        }
        if (fecha.isBefore(lunes)) {
            throw new IllegalArgumentException("Solo puedes ver los días cerrados de esta semana.");
        }
        if (diaUno == null) {
            throw new IllegalArgumentException("Todavía no empezaste el programa: no hay días que ver.");
        }
        if (fecha.isBefore(diaUno)) {
            throw new IllegalArgumentException("Ese día es anterior a tu Día 1.");
        }
    }
}
