package com.renaser.os.habits.domain.model.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Santuario: sesion de bloqueo de un habito BLOQUEO, 1:1 con su
 * {@link RegistroHabitoId} (tabla `sesiones_bloqueo`, PK=FK). Traduccion 1:1 de
 * `blocking.ts` (repo viejo, paso 0 en docs/MODULO_HABITS.md).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "registroHabitoId")
public final class SesionBloqueo {

    /** Minutos minimos por defecto antes de poder completar (blocking.ts:16, DEFAULT_MIN_DURATION_MIN). */
    public static final int DURACION_MINIMA_DEFAULT_MIN = 30;
    /** Puntos que se descuentan al romper la sesion (blocking.ts:17, BREAK_PENALTY_POINTS). */
    public static final int PENALIZACION_ROTURA_PUNTOS = 10;
    /** Gracia tras la hora limite del horario para poder completar (blocking.ts:18, COMPLETE_GRACE_MS). */
    public static final Duration GRACIA_COMPLETAR = Duration.ofMinutes(5);

    private final RegistroHabitoId registroHabitoId;
    private EstadoSesionBloqueo estado;
    private final Instant iniciadaEn;
    private Instant terminadaEn;
    private final int duracionMinimaMin;
    private MotivoSalidaBloqueo motivoSalida;
    private String evidenciaSalidaBucket;
    private String evidenciaSalidaRuta;
    private boolean penalizacionAplicada;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static SesionBloqueo iniciar(RegistroHabitoId registroHabitoId, Instant ahora) {
        return iniciar(registroHabitoId, DURACION_MINIMA_DEFAULT_MIN, ahora);
    }

    public static SesionBloqueo iniciar(RegistroHabitoId registroHabitoId, int duracionMinimaMin, Instant ahora) {
        Objects.requireNonNull(registroHabitoId, "registroHabitoId es obligatorio");
        if (duracionMinimaMin <= 0) {
            throw new IllegalArgumentException("duracionMinimaMin debe ser positivo");
        }
        return new SesionBloqueo(registroHabitoId, EstadoSesionBloqueo.ACTIVA, ahora, null, duracionMinimaMin, null,
                null, null, false, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static SesionBloqueo rehydrate(RegistroHabitoId registroHabitoId, EstadoSesionBloqueo estado,
                                           Instant iniciadaEn, Instant terminadaEn, int duracionMinimaMin,
                                           MotivoSalidaBloqueo motivoSalida, String evidenciaSalidaBucket,
                                           String evidenciaSalidaRuta, boolean penalizacionAplicada,
                                           Instant creadoEn, Instant actualizadoEn) {
        return new SesionBloqueo(registroHabitoId, estado, iniciadaEn, terminadaEn, duracionMinimaMin, motivoSalida,
                evidenciaSalidaBucket, evidenciaSalidaRuta, penalizacionAplicada, creadoEn, actualizadoEn);
    }

    /**
     * Completa la sesion. Exige haber cumplido {@code duracionMinimaMin} Y no
     * haber pasado {@code limiteHorario + GRACIA_COMPLETAR} (blocking.ts:167-183).
     * {@code limiteHorario} es {@code null} cuando el habito no tiene horario
     * configurado (sin limite que exigir, mismo criterio que VentanaEntrega).
     */
    public void completar(Instant ahora, Instant limiteHorario) {
        requireActiva();
        long minutos = Duration.between(iniciadaEn, ahora).toMinutes();
        if (minutos < duracionMinimaMin) {
            throw new IllegalStateException(
                    "Necesitas al menos " + duracionMinimaMin + " minutos en Santuario antes de terminar");
        }
        if (limiteHorario != null && ahora.isAfter(limiteHorario.plus(GRACIA_COMPLETAR))) {
            throw new IllegalStateException("La ventana de tu Santuario ya cerro");
        }
        this.estado = EstadoSesionBloqueo.COMPLETADA;
        this.terminadaEn = ahora;
        this.actualizadoEn = ahora;
    }

    /** Rompe la sesion — penalizacion de {@link #PENALIZACION_ROTURA_PUNTOS} puntos, siempre aplicada. */
    public void romper(MotivoSalidaBloqueo motivo, String evidenciaBucket, String evidenciaRuta, Instant ahora) {
        requireActiva();
        this.estado = EstadoSesionBloqueo.ROTA;
        this.terminadaEn = ahora;
        this.motivoSalida = Objects.requireNonNull(motivo, "motivo es obligatorio al romper la sesion");
        this.evidenciaSalidaBucket = evidenciaBucket;
        this.evidenciaSalidaRuta = evidenciaRuta;
        this.penalizacionAplicada = true;
        this.actualizadoEn = ahora;
    }

    public boolean estaActiva() {
        return estado == EstadoSesionBloqueo.ACTIVA;
    }

    public boolean estaCompletada() {
        return estado == EstadoSesionBloqueo.COMPLETADA;
    }

    public boolean estaRota() {
        return estado == EstadoSesionBloqueo.ROTA;
    }

    private void requireActiva() {
        if (estado != EstadoSesionBloqueo.ACTIVA) {
            throw new IllegalStateException("La sesion ya no esta activa: " + estado);
        }
    }

    @Override
    public String toString() {
        return "SesionBloqueo[" + registroHabitoId + ", " + estado + "]";
    }
}
