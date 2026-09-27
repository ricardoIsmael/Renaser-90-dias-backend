package com.renaser.os.rocks.domain.model.rocasemanal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Semanas de programa: <b>bloques de siete días del programa</b>. Días 1-7 son la semana 1, 8-14 la 2,
 * y así hasta 85-90, que es la 13 (seis días). Nunca hay semana 14.
 *
 * <p><b>Corregido 2026-09-26 (D-192, E-320).</b> Antes contaba semanas calendario lunes-domingo desde
 * {@code fecha_inicio}, con una semana 1 corta si no se empezaba en lunes (portado de
 * {@code week.ts} del repo viejo). Eso tenía dos defectos: con un inicio de miércoles a domingo los
 * últimos días del programa caían en una «semana 14» que {@code RocaSemanal} y el {@code CHECK} de V1
 * rechazan, y no veía {@code dias_ajuste_programa}, así que tras mover el día de alguien su semana
 * quedaba en otro lado («día 35, semana 4»). El dueño decidió que la semana sale del día del
 * programa y lo sigue cuando se ajusta. Para quien empezó un lunes y nunca fue ajustado, las dos
 * cuentas dan exactamente lo mismo.
 *
 * <p><b>El ancla es el primer día efectivo del programa</b>, no {@code fecha_inicio}: se reconstruye
 * con el día de programa de HOY (ya derivado con el ajuste por {@code users.api}) como
 * {@code hoy − (día − 1)}. Así la semana sigue al día sin que {@code rocks} tenga que conocer el
 * ajuste. Límite conocido: pasado el día 90 el día viene acotado a 90 y el ancla asume que hoy es el
 * 90 (solo afecta a quien ya se graduó; ver D-192).
 */
public final class SemanaPrograma {

    public static final int DIAS_POR_SEMANA = 7;
    public static final int ULTIMA_SEMANA = 13;
    public static final int ULTIMO_DIA = 90;

    private final LocalDate primerDia;

    private SemanaPrograma(LocalDate primerDia) {
        this.primerDia = Objects.requireNonNull(primerDia, "primerDia es obligatorio");
    }

    /**
     * @param fechaInicio      {@code participantes_programa.fecha_inicio}
     * @param diaProgramaDeHoy el día de programa de {@code hoy}, ya derivado con el ajuste
     * @param hoy              la fecha de hoy EN LA ZONA del participante
     */
    public static SemanaPrograma desde(LocalDate fechaInicio, int diaProgramaDeHoy, LocalDate hoy) {
        if (hoy.isBefore(fechaInicio)) {
            // El reloj no arrancó: no puede haber ajuste y el día guardado no sirve de ancla.
            return new SemanaPrograma(fechaInicio);
        }
        return new SemanaPrograma(hoy.minusDays(diaProgramaDeHoy - 1L));
    }

    /** La semana (1 a 13) de un día de programa: {@code ceil(día / 7)}, con el día acotado a 1..90. */
    public static int numeroDeDia(int diaPrograma) {
        int dia = Math.min(Math.max(diaPrograma, 1), ULTIMO_DIA);
        return (dia + DIAS_POR_SEMANA - 1) / DIAS_POR_SEMANA;
    }

    /** La fecha que es (o fue, o será) el día 1 del programa, con el ajuste ya aplicado. */
    public LocalDate primerDia() {
        return primerDia;
    }

    /** El número de semana de programa (1 a 13) al que pertenece {@code fecha}. */
    public int numeroSemanaParaFecha(LocalDate fecha) {
        return numeroDeDia(diaDe(fecha));
    }

    /** Inicio y fin de una semana de programa. La 13 termina el día 90 (dura seis días). */
    public LimitesSemana limites(int numeroSemana) {
        int semana = Math.min(Math.max(numeroSemana, 1), ULTIMA_SEMANA);
        LocalDate inicio = primerDia.plusDays((semana - 1L) * DIAS_POR_SEMANA);
        LocalDate fin = inicio.plusDays(DIAS_POR_SEMANA - 1L);
        return new LimitesSemana(inicio, fin.isAfter(finDelPrograma()) ? finDelPrograma() : fin);
    }

    /** Último día del programa: el día 90, contando el ajuste. */
    public LocalDate finDelPrograma() {
        return primerDia.plusDays(ULTIMO_DIA - 1L);
    }

    /**
     * Qué semana se planifica {@code hoy}: la de hoy, salvo el ÚLTIMO día de una semana (7, 14 … 84),
     * que ya prepara la siguiente. Antes del día 1 se planifica la semana 1. Nunca pasa de la 13.
     *
     * <p>Es la traducción directa de la regla vieja («el +1 solo vale el domingo, porque el domingo
     * prepara la semana que empieza mañana») a semanas que ya no terminan en domingo.
     */
    public int semanaAPlanificar(LocalDate hoy) {
        int dia = diaDe(hoy);
        if (dia < 1) {
            return 1;
        }
        int semana = numeroDeDia(dia);
        boolean ultimoDiaDeSuSemana = dia % DIAS_POR_SEMANA == 0;
        return ultimoDiaDeSuSemana ? Math.min(semana + 1, ULTIMA_SEMANA) : semana;
    }

    private int diaDe(LocalDate fecha) {
        return (int) ChronoUnit.DAYS.between(primerDia, fecha) + 1;
    }

    public record LimitesSemana(LocalDate inicio, LocalDate fin) {
    }
}
