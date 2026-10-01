package com.renaser.os.leadership.domain.model.periodo;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Un mes natural en la zona del reporte, como intervalo semiabierto {@code [desde, hasta)} (SDD 002,
 * RNL-04 y PL-06).
 *
 * @param hasta el primer instante del mes siguiente: lo que se consulta es {@code [desde, hasta)}. En el
 *              mes en curso no hay datos después de ahora, así que no hace falta cortar la consulta (y
 *              cortarla en ahora dejaba afuera lo registrado en este mismo instante)
 * @param corte lo que se MUESTRA como «datos al»: el fin del mes o ahora, lo que llegue primero
 */
public record MesDelReporte(YearMonth mes, ZoneId zona, Instant desde, Instant hasta, Instant corte) {

    /**
     * La zona del padrón (el default de {@code participantes_programa.timezone} y de la política de
     * mentoría). El reporte es del cuerpo de mentores, no de una persona: se lee en la hora del programa
     * (propuesta PL-06 del SDD 002, Lima como valor inicial).
     */
    public static final ZoneId ZONA_DEL_PROGRAMA = ZoneId.of("America/Lima");

    public MesDelReporte {
        Objects.requireNonNull(mes, "mes es obligatorio");
        Objects.requireNonNull(zona, "zona es obligatoria");
    }

    /** El mes en curso para alguien en {@code zona}. Nunca la fecha del servidor (regla 02). */
    public static MesDelReporte enCurso(ZoneId zona, Instant ahora) {
        return de(YearMonth.from(ahora.atZone(zona)), zona, ahora);
    }

    /** @param texto {@code YYYY-MM}; null = el mes en curso */
    public static MesDelReporte pedido(String texto, ZoneId zona, Instant ahora) {
        if (texto == null || texto.isBlank()) {
            return enCurso(zona, ahora);
        }
        YearMonth mes;
        try {
            mes = YearMonth.parse(texto.trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("El mes va como AAAA-MM, por ejemplo 2026-10: " + texto);
        }
        if (mes.isAfter(YearMonth.from(ahora.atZone(zona)))) {
            throw new IllegalArgumentException("Ese mes todavia no empezo: " + texto);
        }
        return de(mes, zona, ahora);
    }

    private static MesDelReporte de(YearMonth mes, ZoneId zona, Instant ahora) {
        Instant desde = mes.atDay(1).atStartOfDay(zona).toInstant();
        Instant fin = mes.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant();
        return new MesDelReporte(mes, zona, desde, fin, ahora.isBefore(fin) ? ahora : fin);
    }

    /** Si el mes ya terminó: sus cifras ya no cambian por el paso del tiempo. */
    public boolean cerrado(Instant ahora) {
        return !ahora.isBefore(hasta);
    }

    public boolean contiene(Instant instante) {
        return !instante.isBefore(desde) && instante.isBefore(hasta);
    }
}
