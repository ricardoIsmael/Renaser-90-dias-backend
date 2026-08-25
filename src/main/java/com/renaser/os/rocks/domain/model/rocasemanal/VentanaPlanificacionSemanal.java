package com.renaser.os.rocks.domain.model.rocasemanal;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Ventana de planificación semanal ("Domingo Ritual") y su margen de edición
 * a destiempo. Pura: recibe el instante y la zona horaria del participante,
 * nunca lee el reloj del sistema — eso es responsabilidad de quien la llama
 * (vía {@code shared.domain.Clock}).
 *
 * <p>Portado de `src/features/rocks/plazos.ts` + `service.ts:1132-1199` del
 * repo viejo. Reemplaza, desde 2026-08-07, la ventana FIJA de 48 h que tenía
 * el blueprint original — ver decisión RK-5 en `docs/MODULO_ROCKS.md`: el
 * nombre de {@code EditarDentroDe48hUseCase} se conserva tal cual lo pidió el
 * encargo, pero la regla real de negocio es esta, no 48 h fijas.
 */
public final class VentanaPlanificacionSemanal {

    /** La ventana abre el domingo a esta hora local. */
    public static final int ABRE_HORA_DOMINGO = 12;
    /** ...y cierra el lunes a esta hora local ("en punto" ya es tarde). */
    public static final int CIERRA_HORA_LUNES = 9;
    /** Margen para rectificar una planificación hecha a destiempo. */
    public static final Duration MARGEN_TARDIO = Duration.ofHours(2);

    private VentanaPlanificacionSemanal() {
    }

    /** ¿Está la ventana abierta en este instante, en la zona del participante? */
    public static boolean abierta(Instant instante, ZoneId zona) {
        ZonedDateTime local = instante.atZone(zona);
        DayOfWeek dia = local.getDayOfWeek();
        int hora = local.getHour();
        if (dia == DayOfWeek.SUNDAY) {
            return hora >= ABRE_HORA_DOMINGO;
        }
        if (dia == DayOfWeek.MONDAY) {
            return hora < CIERRA_HORA_LUNES;
        }
        return false;
    }

    /** El plazo con el que cuenta algo creado en este instante. */
    public static EstadoPlazo plazoAlCrear(Instant creadoEn, ZoneId zona) {
        return abierta(creadoEn, zona) ? EstadoPlazo.EN_PLAZO : EstadoPlazo.A_DESTIEMPO;
    }

    /**
     * ¿Se puede editar todavía?
     *
     * <p>Si la ventana normal está abierta AHORA, se puede editar sin importar
     * cómo se creó (caso mixto: se llenó tarde el sábado y el domingo a la
     * tarde la ventana ya abrió sola). Si no, manda el plazo con el que se
     * creó: en plazo ya no se puede tocar; a destiempo hay 2 h de margen.
     */
    public static boolean puedeEditar(EstadoPlazo plazoAlCrear, Instant creadoEn, Instant ahora, ZoneId zona) {
        if (abierta(ahora, zona)) {
            return true;
        }
        if (plazoAlCrear == EstadoPlazo.EN_PLAZO) {
            return false;
        }
        return !ahora.isAfter(limiteTardio(creadoEn, zona));
    }

    /**
     * Hasta cuándo se puede rectificar algo creado a destiempo: creación + 2 h,
     * o el final del día local de la creación — lo que ocurra primero. El tope
     * evita que editar a las 23:00 dé margen hasta la madrugada del día siguiente.
     */
    public static Instant limiteTardio(Instant creadoEn, ZoneId zona) {
        Instant finDelDia = finDelDiaLocal(creadoEn, zona);
        Instant limite = creadoEn.plus(MARGEN_TARDIO);
        return limite.isBefore(finDelDia) ? limite : finDelDia;
    }

    private static Instant finDelDiaLocal(Instant instante, ZoneId zona) {
        ZonedDateTime local = instante.atZone(zona);
        return local.toLocalDate().plusDays(1).atStartOfDay(zona).toInstant().minusNanos(1);
    }
}
