package com.renaser.os.rag.domain.model.propuesta;

import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Una escritura que el acompanante propuso y que solo la persona puede disparar con un boton
 * (fase 2, D-153; tabla {@code propuestas_acompanante}, V63).
 *
 * <p>Ciclo de vida:
 * <pre>
 *   PENDIENTE --confirmar--> CONFIRMADA --registrarResultado--> CONFIRMADA (con resultado)
 *       |                        \--marcarFallida--> FALLIDA
 *       |--marcarFallida--> FALLIDA   (no hubo como ejecutarla)
 *       \--cancelar--> CANCELADA
 * </pre>
 * Vencer no es un estado: una PENDIENTE con {@code venceEn} ya pasado esta vencida
 * ({@link #estaVencidaEn}), y ninguna transicion hacia CONFIRMADA la acepta.
 *
 * <p>{@code version} es el control de concurrencia optimista que mantiene la persistencia; el
 * dominio solo lo transporta. Es lo que hace que un doble toque sobre "Confirmar" ejecute una vez.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class PropuestaAccion {

    private final PropuestaAccionId id;
    private final UserId participanteId;
    private final InvocacionHerramienta invocacion;
    private final HuellaArgumentos huella;
    private final String resumen;
    private EstadoPropuesta estado;
    private final Instant creadaEn;
    private final Instant venceEn;
    private Instant resueltaEn;
    private String resultado;
    /** {@code null} mientras no se guardo nunca. */
    private final Long version;

    /**
     * Propuesta nueva, PENDIENTE, que vence {@code vigencia} despues de {@code ahora}. La huella se
     * calcula aca, sobre los mismos argumentos que se guardan.
     */
    public static PropuestaAccion crear(PropuestaAccionId id, UserId participanteId, InvocacionHerramienta invocacion,
                                        String resumen, Instant ahora, Duration vigencia) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(invocacion, "invocacion es obligatoria");
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        requireTexto(invocacion.nombre(), "la herramienta");
        requireTexto(resumen, "el resumen");
        requireVigenciaPositiva(vigencia);
        HuellaArgumentos huella = HuellaArgumentos.de(invocacion.nombre(), invocacion.argumentos());
        return new PropuestaAccion(id, participanteId, invocacion, huella, resumen.strip(), EstadoPropuesta.PENDIENTE,
                ahora, ahora.plus(vigencia), null, null, null);
    }

    /** Reconstruye desde persistencia, sin volver a validar: la integridad se verifica al confirmar. */
    public static PropuestaAccion rehidratar(PropuestaAccionId id, UserId participanteId,
                                             InvocacionHerramienta invocacion, HuellaArgumentos huella,
                                             String resumen, EstadoPropuesta estado, Instant creadaEn,
                                             Instant venceEn, Instant resueltaEn, String resultado, Long version) {
        return new PropuestaAccion(id, participanteId, invocacion, huella, resumen, estado, creadaEn, venceEn,
                resueltaEn, resultado, version);
    }

    public boolean perteneceA(UserId actorId) {
        return participanteId.equals(actorId);
    }

    /** Derivado del reloj: solo una PENDIENTE puede estar vencida, y lo esta desde {@code venceEn} inclusive. */
    public boolean estaVencidaEn(Instant ahora) {
        return estado == EstadoPropuesta.PENDIENTE && !ahora.isBefore(venceEn);
    }

    /** Los argumentos guardados son exactamente los que se propusieron. */
    public boolean argumentosIntegros() {
        return huella.coincideCon(invocacion.nombre(), invocacion.argumentos());
    }

    /** CONFIRMADA sin resultado: alguien ya la esta ejecutando (o la ejecucion se corto). */
    public boolean enEjecucion() {
        return estado == EstadoPropuesta.CONFIRMADA && resultado == null;
    }

    /** Lo que devolvio la ejecucion, si ya termino. Es lo que hace idempotente a la confirmacion. */
    public Optional<ResultadoHerramienta> resultadoRegistrado() {
        if (estado == EstadoPropuesta.FALLIDA) {
            return Optional.of(ResultadoHerramienta.fallo(resultado));
        }
        if (estado == EstadoPropuesta.CONFIRMADA && resultado != null) {
            return Optional.of(ResultadoHerramienta.exito(resultado));
        }
        return Optional.empty();
    }

    /** PENDIENTE y no vencida pasa a CONFIRMADA; se guarda ANTES de ejecutar la accion. */
    public void confirmar(Instant ahora) {
        requirePendiente("confirmar");
        if (estaVencidaEn(ahora)) {
            throw new PropuestaNoDisponibleException(
                    "Esta propuesta vencio. Pidele al acompanante que te la vuelva a ofrecer.");
        }
        estado = EstadoPropuesta.CONFIRMADA;
        resueltaEn = ahora;
    }

    /** Anota lo que devolvio la accion ya ejecutada. */
    public void registrarResultado(String contenido) {
        if (!enEjecucion()) {
            throw new IllegalStateException("Solo se registra el resultado de una propuesta recien confirmada");
        }
        requireTexto(contenido, "el resultado");
        resultado = contenido;
    }

    /** No se pudo ejecutar; {@code motivo} es legible y es lo que ve la persona. */
    public void marcarFallida(String motivo, Instant ahora) {
        if (estado != EstadoPropuesta.PENDIENTE && !enEjecucion()) {
            throw new IllegalStateException("Solo falla una propuesta pendiente o en ejecucion, no una " + estado);
        }
        requireTexto(motivo, "el motivo");
        estado = EstadoPropuesta.FALLIDA;
        resultado = motivo;
        resueltaEn = resueltaEn != null ? resueltaEn : ahora;
    }

    /**
     * Cancelar es idempotente (una CANCELADA se queda como esta) y se permite aunque haya vencido:
     * no ejecuta nada. Lo que no se puede es cancelar algo que ya se ejecuto.
     */
    public void cancelar(Instant ahora) {
        if (estado == EstadoPropuesta.CANCELADA) {
            return;
        }
        requirePendiente("cancelar");
        estado = EstadoPropuesta.CANCELADA;
        resueltaEn = ahora;
    }

    private void requirePendiente(String operacion) {
        switch (estado) {
            case PENDIENTE -> {
            }
            case CANCELADA -> throw new PropuestaNoDisponibleException(
                    "Esta propuesta ya fue cancelada; no se puede " + operacion + ".");
            case CONFIRMADA, FALLIDA -> throw new PropuestaNoDisponibleException(
                    "Esta propuesta ya se resolvio; no se puede " + operacion + ".");
        }
    }

    private static void requireTexto(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + nombre + " de la propuesta");
        }
    }

    private static void requireVigenciaPositiva(Duration vigencia) {
        if (vigencia == null || vigencia.isZero() || vigencia.isNegative()) {
            throw new IllegalArgumentException("La vigencia de una propuesta tiene que ser positiva: " + vigencia);
        }
    }
}
