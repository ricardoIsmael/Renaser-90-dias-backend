package com.renaser.os.rocks.domain.model.verdugo;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento Verdugo: el aprendiz reacciona (o no) cuando se le vence el plazo de
 * una roca diaria o un hábito. Portado de `src/features/enforcer/*` del repo
 * viejo — `IGNORADO` nunca lo manda el cliente, lo asigna el barrido nocturno.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class EventoVerdugo {

    private final EventoVerdugoId id;
    private final UserId participanteId;
    private final DestinoVerdugo destinoTipo;
    private final UUID destinoId;
    private final Instant disparadoEn;
    private ResultadoVerdugo resultado;
    private Instant resueltoEn;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /**
     * Registra un evento ya resuelto por el aprendiz (mismo contrato que
     * `POST /api/v1/enforcer-events` del repo viejo: el cliente siempre manda
     * el resultado, nunca queda pendiente). {@code IGNORADO} se rechaza —
     * es exclusivo del barrido nocturno.
     *
     * <p>El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code VerdugoService.registrar}). Asi la
     * factoria es referencialmente transparente y un test puede fijar el id que espera, en
     * vez de tener que caer a {@link #rehydrate} para lograrlo (CLAUDE.MD §5.4.7).
     */
    public static EventoVerdugo registrar(EventoVerdugoId id, UserId participanteId,
                                           DestinoVerdugo destinoTipo, UUID destinoId, Instant disparadoEn,
                                           ResultadoVerdugo resultado, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(destinoTipo, "destinoTipo es obligatorio");
        Objects.requireNonNull(destinoId, "destinoId es obligatorio");
        Objects.requireNonNull(disparadoEn, "disparadoEn es obligatorio");
        requireResultadoDeCliente(resultado);
        Instant ahora = clock.now();
        return new EventoVerdugo(id, participanteId, destinoTipo, destinoId, disparadoEn,
                resultado, ahora, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia: reconstruye un evento ya existente. */
    public static EventoVerdugo rehydrate(EventoVerdugoId id, UserId participanteId, DestinoVerdugo destinoTipo,
                                           UUID destinoId, Instant disparadoEn, ResultadoVerdugo resultado,
                                           Instant resueltoEn, Instant creadoEn, Instant actualizadoEn) {
        return new EventoVerdugo(id, participanteId, destinoTipo, destinoId, disparadoEn, resultado, resueltoEn,
                creadoEn, actualizadoEn);
    }

    /** Barrido de las 23:55: cualquier evento sin resultado ese día pasa a IGNORADO. */
    public void resolverComoIgnorado(Clock clock) {
        if (resultado != null) {
            throw new IllegalStateException("El evento ya estaba resuelto: " + resultado);
        }
        this.resultado = ResultadoVerdugo.IGNORADO;
        this.resueltoEn = clock.now();
        this.actualizadoEn = clock.now();
    }

    public boolean pendiente() {
        return resultado == null;
    }

    private static void requireResultadoDeCliente(ResultadoVerdugo resultado) {
        Objects.requireNonNull(resultado, "resultado es obligatorio");
        if (resultado == ResultadoVerdugo.IGNORADO) {
            throw new IllegalArgumentException("IGNORADO lo asigna el barrido nocturno, no el cliente");
        }
    }

    @Override
    public String toString() {
        return "EventoVerdugo[" + id + ", " + destinoTipo + " " + destinoId + ", " + resultado + "]";
    }
}
