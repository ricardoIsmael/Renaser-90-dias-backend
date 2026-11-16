package com.renaser.os.shared.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Spec 048 (FR-013 a FR-015, D13, D14): cómo se lee la hora real de lo hecho sin red. Una sola clase para todas las áreas de la
 * fase 3, <b>sin reloj propio</b>: quien la arma pasa {@code ahora} (el {@code Clock} del caso de uso).
 *
 * <ul>
 *   <li>{@link #referencia()} = {@code min(hechoEn, ahora)}: <b>todas las reglas de calendario del caso de uso corren con ella</b>,
 *       nunca con {@code clock.now()}. Una hora futura no adelanta nada: se usa {@code ahora}.</li>
 *   <li>{@link #esHoraFutura()}: {@code hechoEn} pasa de {@code ahora} por más de 2 minutos. Lo que da puntos queda sin ellos;
 *       lo demás se aplica con {@code referencia = ahora}.</li>
 *   <li>{@link #llegoTarde()}: más de 48 horas desde {@code hechoEn}. Solo quita puntos (P3): lo que no da puntos no tiene
 *       tope de llegada.</li>
 *   <li>{@link #hayCambioMasNuevo(Instant)}: la regla de choques (D13): una fila que cambió DESPUÉS de {@code hechoEn} gana.</li>
 * </ul>
 */
public final class RevisionDeLaHora {

    /** Cuánto se tolera una hora «del futuro» (relojes que no coinciden por segundos). */
    public static final Duration TOLERANCIA_DEL_FUTURO = Duration.ofMinutes(2);
    /** Lo que llega después de esto, si daba puntos, queda registrado sin ellos. */
    public static final Duration PLAZO_DE_LLEGADA = Duration.ofHours(48);

    private final Instant hechoEn;
    private final Instant ahora;

    private RevisionDeLaHora(Instant hechoEn, Instant ahora) {
        this.hechoEn = Objects.requireNonNull(hechoEn, "hechoEn");
        this.ahora = Objects.requireNonNull(ahora, "ahora");
    }

    public static RevisionDeLaHora de(HechoSinRed sinRed, Instant ahora) {
        return new RevisionDeLaHora(sinRed.hechoEn(), ahora);
    }

    public static RevisionDeLaHora de(Instant hechoEn, Instant ahora) {
        return new RevisionDeLaHora(hechoEn, ahora);
    }

    public Instant hechoEn() {
        return hechoEn;
    }

    public Instant ahora() {
        return ahora;
    }

    /** {@code min(hechoEn, ahora)}: el instante con el que corren las reglas de calendario. */
    public Instant referencia() {
        return hechoEn.isBefore(ahora) ? hechoEn : ahora;
    }

    public boolean esHoraFutura() {
        return hechoEn.isAfter(ahora.plus(TOLERANCIA_DEL_FUTURO));
    }

    public boolean llegoTarde() {
        return Duration.between(hechoEn, ahora).compareTo(PLAZO_DE_LLEGADA) > 0;
    }

    /**
     * Por qué lo que daba puntos queda sin ellos (D14), o vacío si está en regla: primero la hora futura, después las 48 h y por
     * último que el día de lo hecho no sea el de la hora real. Es el mismo orden de los hábitos de la fase 1.
     *
     * @param diaDeLoHecho el día al que pertenece lo que se marcó (por ejemplo la fecha de una acción)
     */
    public Optional<MotivoSinPuntos> motivoSinPuntos(LocalDate diaDeLoHecho, ZoneId zona) {
        if (esHoraFutura()) {
            return Optional.of(MotivoSinPuntos.HORA_FUTURA);
        }
        if (llegoTarde()) {
            return Optional.of(MotivoSinPuntos.LLEGO_DESPUES_DE_48_H);
        }
        return diaDeLoHecho.equals(hoyEn(zona)) ? Optional.empty() : Optional.of(MotivoSinPuntos.FUERA_DE_SU_DIA);
    }

    /** «Hoy» de la persona a la hora real (regla 02): el día local de {@link #referencia()}. */
    public LocalDate hoyEn(ZoneId zona) {
        return referencia().atZone(zona).toLocalDate();
    }

    /** El «hoy» de la llegada: el día local de {@code ahora}. */
    public LocalDate hoyDeLaLlegadaEn(ZoneId zona) {
        return ahora.atZone(zona).toLocalDate();
    }

    /**
     * El día del programa a la hora real: el de hoy menos los días entre el «hoy» de la hora real y el de la llegada, con piso 0.
     * No reconstruye los ajustes del reloj de esa semana (riesgo R4 del plan).
     */
    public int diaDelPrograma(int diaDeHoy, ZoneId zona) {
        long dias = ChronoUnit.DAYS.between(hoyEn(zona), hoyDeLaLlegadaEn(zona));
        return (int) Math.max(0, diaDeHoy - dias);
    }

    /**
     * D13: ¿cambió esa fila después de lo que hiciste? Una fila sin hora ({@code null}) es «muy vieja». La comparación es estricta:
     * un envío del mismo teléfono deja {@code actualizado_en = referencia} (su {@code hechoEn}) y el siguiente, con un
     * {@code hechoEn} igual o posterior, no choca contra él.
     */
    public boolean hayCambioMasNuevo(Instant actualizadoEn) {
        return actualizadoEn != null && actualizadoEn.isAfter(hechoEn);
    }
}
