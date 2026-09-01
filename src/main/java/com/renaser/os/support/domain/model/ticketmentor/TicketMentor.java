package com.renaser.os.support.domain.model.ticketmentor;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class TicketMentor {

    private final TicketMentorId id;
    private final UserId participanteId;
    private final String descripcionBloqueo;
    private final String solucionesIntentadas;
    private final String impactoMetaSmart;
    private EstadoTicketMentor estado;
    private String respuestaMentor;
    private Instant respondidoEn;
    private boolean guardadoEnBiblioteca;
    private final Instant creadoEn;

    /**
     * Abre un ticket nuevo, siempre ABIERTO, nunca guardado en biblioteca.
     *
     * <p>El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso. Asi la factoria es referencialmente
     * transparente y un test puede fijar el id que espera (CLAUDE.MD §5.4.7).
     */
    public static TicketMentor abrir(TicketMentorId id, UserId participanteId, String descripcionBloqueo,
                                      String solucionesIntentadas, String impactoMetaSmart, Clock clock) {
        return new TicketMentor(Objects.requireNonNull(id, "id es obligatorio"),
                Objects.requireNonNull(participanteId, "participanteId es obligatorio"),
                requireNotBlank(descripcionBloqueo, "La descripcion del bloqueo es obligatoria"),
                requireNotBlank(solucionesIntentadas, "Las soluciones intentadas son obligatorias"),
                requireNotBlank(impactoMetaSmart, "El impacto en la meta SMART es obligatorio"),
                EstadoTicketMentor.ABIERTO, null, null, false, clock.now());
    }

    /** Solo para el adaptador de persistencia: reconstruye un ticket ya existente. */
    public static TicketMentor rehydrate(TicketMentorId id, UserId participanteId, String descripcionBloqueo,
                                          String solucionesIntentadas, String impactoMetaSmart,
                                          EstadoTicketMentor estado, String respuestaMentor, Instant respondidoEn,
                                          boolean guardadoEnBiblioteca, Instant creadoEn) {
        return new TicketMentor(id, participanteId, descripcionBloqueo, solucionesIntentadas, impactoMetaSmart,
                estado, respuestaMentor, respondidoEn, guardadoEnBiblioteca, creadoEn);
    }

    /** ABIERTO -> RESPONDIDO. Nunca se reabre. */
    public void responder(String respuesta, Clock clock) {
        requireAbierto();
        this.respuestaMentor = requireNotBlank(respuesta, "La respuesta no puede ser vacia");
        this.estado = EstadoTicketMentor.RESPONDIDO;
        this.respondidoEn = clock.now();
    }

    /** Exige RESPONDIDO — mismo invariante que el CHECK `respondido_coherente` del SQL. */
    public void guardarEnBiblioteca() {
        if (!estado.estaRespondido()) {
            throw new IllegalStateException("El ticket todavia no tiene respuesta");
        }
        this.guardadoEnBiblioteca = true;
    }

    private void requireAbierto() {
        if (!estado.estaAbierto()) {
            throw new IllegalStateException("El ticket ya fue respondido: " + estado);
        }
    }

    private static String requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "TicketMentor[" + id + ", " + participanteId + ", " + estado + "]";
    }
}
