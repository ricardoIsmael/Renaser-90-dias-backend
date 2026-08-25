package com.renaser.os.onboarding.domain.model.grabacionv90;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Uno de los 9 audios V90 (tabla {@code grabaciones_v90}), identificado por
 * {@code (usuarioId, fase, eje, indice)} — UNIQUE del baseline. Autocontenida: NO depende
 * del modulo {@code evidence} (que se construye en paralelo), tiene su PROPIA maquina de
 * estados de validacion IA, analoga en forma pero independiente en codigo.
 *
 * <p><b>Maquina de estados de {@code estadoIa}</b> (CLAUDE.MD, patron de reintentos con
 * fallback a revision manual, replicado aca como regla de DOMINIO):
 * <pre>
 * PENDIENTE --procesarIntentoDeValidacion()--> PROCESANDO
 * PROCESANDO --registrarAprobacion()--> APROBADA (final)
 * PROCESANDO --registrarRechazo()------> RECHAZADA (final)
 * PROCESANDO --registrarSinResultado()-->  PENDIENTE   (si intentosIa &lt; 3, reintentable)
 *                                       -> REVISION_MANUAL (si intentosIa ya llego a 3)
 * </pre>
 * En este alcance (SIN integracion de IA real, ver docs/MODULO_ONBOARDING.md) el puerto
 * {@code ValidacionIAPort} siempre devuelve "no disponible", asi que toda grabacion termina
 * en {@code REVISION_MANUAL} tras 3 intentos — la maquina de estados esta completa y
 * probada, lo que falta es el adaptador real de IA.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class GrabacionV90 {

    /** Espejo del CHECK {@code intentos_ia BETWEEN 0 AND 3} del baseline. */
    public static final short MAX_INTENTOS = 3;

    private final Long id;
    private final UserId usuarioId;
    private final String fase;
    private final String eje;
    private final short indice;
    private final String clavePregunta;
    private boolean grabada;
    private Long mediaId;
    private BigDecimal duracionSegundos;
    private String transcripcion;
    private EstadoIAv90 estadoIa;
    private short intentosIa;
    private String feedbackIa;
    private Instant grabadaEn;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /** Placeholder para el slot (usuarioId, fase, eje, indice), todavia sin audio. */
    public static GrabacionV90 crearSlot(UserId usuarioId, String fase, String eje, short indice,
                                          String clavePregunta, Clock clock) {
        Instant ahora = clock.now();
        return new GrabacionV90(null, requireUsuarioId(usuarioId), requireTexto(fase, "fase"),
                requireTexto(eje, "eje"), requireIndiceValido(indice), clavePregunta, false, null, null, null,
                EstadoIAv90.PENDIENTE, (short) 0, null, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static GrabacionV90 rehydrate(Long id, UserId usuarioId, String fase, String eje, short indice,
                                          String clavePregunta, boolean grabada, Long mediaId,
                                          BigDecimal duracionSegundos, String transcripcion, EstadoIAv90 estadoIa,
                                          short intentosIa, String feedbackIa, Instant grabadaEn, Instant creadoEn,
                                          Instant actualizadoEn) {
        return new GrabacionV90(id, usuarioId, fase, eje, indice, clavePregunta, grabada, mediaId, duracionSegundos,
                transcripcion, estadoIa, intentosIa, feedbackIa, grabadaEn, creadoEn, actualizadoEn);
    }

    /**
     * Registra (o vuelve a registrar) el audio de este slot. Un re-grabado invalida
     * cualquier validacion previa: vuelve a {@code PENDIENTE} con 0 intentos — es una
     * grabacion distinta, no tiene sentido conservar el veredicto de la anterior.
     */
    public void marcarGrabada(long mediaId, BigDecimal duracionSegundos, String transcripcion, Clock clock) {
        this.grabada = true;
        this.mediaId = mediaId;
        this.duracionSegundos = duracionSegundos;
        this.transcripcion = transcripcion;
        this.estadoIa = EstadoIAv90.PENDIENTE;
        this.intentosIa = 0;
        this.feedbackIa = null;
        Instant ahora = clock.now();
        this.grabadaEn = ahora;
        this.actualizadoEn = ahora;
    }

    /**
     * Arranca un intento de validacion. Debe llamarse ANTES de invocar a ValidacionIAPort.
     * Bloquea reentrada mientras ya hay un intento {@code PROCESANDO} (auditoria de
     * concurrencia, docs/BITACORA_ERRORES.md E-37): sin este guard, un doble despacho del
     * cliente (timeout + reintento) podia arrancar dos intentos async en paralelo, y el
     * que terminara despues pisaba en silencio el veredicto que ya habia registrado el
     * primero — incluso si ese primero ya era {@code APROBADA}/{@code RECHAZADA}, porque
     * {@code registrarAprobacion}/{@code registrarRechazo}/{@code registrarSinResultado}
     * tampoco validaban el estado de entrada.
     */
    public void procesarIntentoDeValidacion(Clock clock) {
        if (!grabada) {
            throw new IllegalStateException("No se puede validar un slot sin audio grabado todavia");
        }
        if (estadoIa == EstadoIAv90.APROBADA || estadoIa == EstadoIAv90.RECHAZADA) {
            throw new IllegalStateException("Esta grabacion ya tiene un veredicto final: " + estadoIa);
        }
        if (estadoIa == EstadoIAv90.PROCESANDO) {
            throw new IllegalStateException("Ya hay un intento de validacion en curso para esta grabacion");
        }
        if (intentosIa >= MAX_INTENTOS) {
            throw new IllegalStateException("Ya se agotaron los " + MAX_INTENTOS + " intentos de validacion");
        }
        this.intentosIa = (short) (intentosIa + 1);
        this.estadoIa = EstadoIAv90.PROCESANDO;
        this.actualizadoEn = clock.now();
    }

    public void registrarAprobacion(String feedbackJson, Clock clock) {
        requireEnProcesando();
        this.estadoIa = EstadoIAv90.APROBADA;
        this.feedbackIa = feedbackJson;
        this.actualizadoEn = clock.now();
    }

    public void registrarRechazo(String feedbackJson, Clock clock) {
        requireEnProcesando();
        this.estadoIa = EstadoIAv90.RECHAZADA;
        this.feedbackIa = feedbackJson;
        this.actualizadoEn = clock.now();
    }

    /** El intento no pudo completarse (IA no disponible, error, timeout) — decide reintentar o caer a revision manual. */
    public void registrarSinResultado(Clock clock) {
        requireEnProcesando();
        this.estadoIa = intentosIa >= MAX_INTENTOS ? EstadoIAv90.REVISION_MANUAL : EstadoIAv90.PENDIENTE;
        this.actualizadoEn = clock.now();
    }

    /** Ninguna de las tres resoluciones de un intento tiene sentido si no hay un intento
     * {@code PROCESANDO} en curso — evita que un despacho async duplicado o tardio
     * sobrescriba un veredicto que ya se resolvio (E-37). */
    private void requireEnProcesando() {
        if (estadoIa != EstadoIAv90.PROCESANDO) {
            throw new IllegalStateException(
                    "No hay un intento de validacion en curso para resolver (estado actual: " + estadoIa + ")");
        }
    }

    private static UserId requireUsuarioId(UserId usuarioId) {
        return Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
    }

    private static String requireTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        return valor;
    }

    private static short requireIndiceValido(short indice) {
        if (indice < 0) {
            throw new IllegalArgumentException("indice no puede ser negativo: " + indice);
        }
        return indice;
    }

    @Override
    public String toString() {
        return "GrabacionV90[" + id + ", " + usuarioId + ", " + fase + "/" + eje + "/" + indice + ", "
                + estadoIa + "]";
    }
}
