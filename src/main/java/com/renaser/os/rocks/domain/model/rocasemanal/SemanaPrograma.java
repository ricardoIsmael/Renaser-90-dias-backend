package com.renaser.os.rocks.domain.model.rocasemanal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Semanas de programa: <b>semanas calendario de LUNES A DOMINGO</b> (D-203), contadas desde la que
 * contiene el primer día efectivo del programa. La semana 1 va del día 1 al primer domingo (corta si no
 * se empezó un lunes) y desde ahí cada semana es de lunes a domingo. <b>Nunca hay semana 14</b>: los días
 * que caerían en ella (inicio de miércoles a domingo) se suman a la 13, que siempre termina el día 90 y
 * dura de 6 a 12 días según el día de la semana en que se empezó.
 *
 * <p>Es de calendario porque el programa define el domingo como el día de cierre y descanso, y el
 * Domingo Ritual ({@link VentanaPlanificacionSemanal}, domingo 12:00 a lunes 09:00) prepara la semana
 * que empieza el lunes: con semanas de calendario las dos cosas coinciden para todos.
 *
 * <p><b>El ancla es el primer día efectivo, no {@code fecha_inicio}.</b> Si a alguien se le ajustó el día,
 * la semana acompaña el ajuste (no vuelve el «día 35, semana 4» de E-320). El ancla la resuelve quien ve
 * el ajuste ({@code ProgresoParticipanteRocks.primerDiaEfectivo}); acá solo se cuenta el calendario. Sin
 * ajuste, el ancla es {@code fecha_inicio} y esta cuenta da <b>exactamente</b> la de producción anterior a
 * D-192 para las semanas 1 a 13: las {@code rocas_semanales} ya guardadas siguen significando lo mismo.
 * Lo único distinto es que lo que aquella contaba como semana 14 o más (que el {@code CHECK} de V1 nunca
 * dejó guardar) ahora es la 13.
 *
 * <p><b>Corregido 2026-09-27 (D-203).</b> D-192 (2026-09-26, no llegó a producción) la había convertido en
 * bloques de siete días del programa: «Días 1-7 son la semana 1, 8-14 la 2, y así hasta 85-90, que es la
 * 13 (seis días)», con el {@code +1} de la planificación en el último día de cada bloque (7, 14 … 84) en
 * vez del domingo. El dueño decidió volver a lunes a domingo para todos; de D-192 se conserva lo que
 * arregló: ni semana 14 ni semana corrida tras un ajuste.
 */
public final class SemanaPrograma {

    public static final int ULTIMA_SEMANA = 13;
    public static final int ULTIMO_DIA = 90;

    private static final int DIAS_POR_SEMANA = 7;

    private final LocalDate primerDia;
    private final LocalDate lunesDeLaSemanaUno;

    private SemanaPrograma(LocalDate primerDia) {
        this.primerDia = Objects.requireNonNull(primerDia, "primerDia es obligatorio");
        this.lunesDeLaSemanaUno = primerDia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * @param primerDia el día 1 del programa con el ajuste ya aplicado ({@code fecha_inicio} si no hubo
     *                  ajuste o si el reloj todavía no arrancó)
     */
    public static SemanaPrograma desde(LocalDate primerDia) {
        return new SemanaPrograma(primerDia);
    }

    /** La fecha que es (o fue, o será) el día 1 del programa, con el ajuste ya aplicado. */
    public LocalDate primerDia() {
        return primerDia;
    }

    /** Último día del programa: el día 90, contando el ajuste. */
    public LocalDate finDelPrograma() {
        return primerDia.plusDays(ULTIMO_DIA - 1L);
    }

    /**
     * La semana (1 a 13) de {@code fecha}: cuántos lunes pasaron desde la semana del día 1. Lo anterior al
     * día 1 cuenta como la 1, y todo lo que va del lunes de la 13 en adelante (incluido lo que queda después
     * del día 90) como la 13.
     */
    public int numeroSemanaParaFecha(LocalDate fecha) {
        long lunesTranscurridos = Math.floorDiv(ChronoUnit.DAYS.between(lunesDeLaSemanaUno, fecha), DIAS_POR_SEMANA);
        return Math.clamp(lunesTranscurridos + 1, 1, ULTIMA_SEMANA);
    }

    /**
     * Inicio y fin de una semana. La 1 empieza el día 1; de la 2 a la 12 van de lunes a domingo; la 13
     * empieza su lunes y termina el día 90, sume los días que sume.
     */
    public LimitesSemana limites(int numeroSemana) {
        int semana = Math.clamp(numeroSemana, 1, ULTIMA_SEMANA);
        LocalDate lunes = lunesDeLaSemanaUno.plusWeeks(semana - 1L);
        LocalDate inicio = semana == 1 ? primerDia : lunes;
        LocalDate fin = semana == ULTIMA_SEMANA ? finDelPrograma() : lunes.plusDays(DIAS_POR_SEMANA - 1L);
        return new LimitesSemana(inicio, fin);
    }

    /**
     * Qué semana se planifica {@code hoy}: la de hoy, salvo el DOMINGO, que ya prepara la que empieza el
     * lunes (el Domingo Ritual). Antes del día 1 se planifica la 1, aunque sea domingo: la semana que
     * empieza el lunes es la primera. Nunca pasa de la 13: un domingo que cae dentro de la 13 la vuelve a
     * pedir a ella.
     */
    public int semanaAPlanificar(LocalDate hoy) {
        if (hoy.isBefore(primerDia)) {
            return 1;
        }
        int semana = numeroSemanaParaFecha(hoy);
        return hoy.getDayOfWeek() == DayOfWeek.SUNDAY ? Math.min(semana + 1, ULTIMA_SEMANA) : semana;
    }

    public record LimitesSemana(LocalDate inicio, LocalDate fin) {
    }
}
