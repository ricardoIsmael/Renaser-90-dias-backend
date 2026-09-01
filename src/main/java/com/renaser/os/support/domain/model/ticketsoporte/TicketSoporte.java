package com.renaser.os.support.domain.model.ticketsoporte;

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
public final class TicketSoporte {

    private final TicketSoporteId id;
    private final UserId usuarioId;
    private final CategoriaSoporte categoria;
    private final String asunto;
    private final String mensaje;
    private final String logCliente;
    private AdjuntoSoporte adjunto;
    private EstadoTicketSoporte estado;
    private String notasAdmin;
    private Instant resueltoEn;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso. Asi la factoria es referencialmente
     * transparente y un test puede fijar el id que espera (CLAUDE.MD §5.4.7).
     */
    public static TicketSoporte abrir(TicketSoporteId id, UserId usuarioId, CategoriaSoporte categoria,
                                       String asunto, String mensaje, String logCliente, AdjuntoSoporte adjunto,
                                       Clock clock) {
        Instant now = clock.now();
        return new TicketSoporte(Objects.requireNonNull(id, "id es obligatorio"),
                Objects.requireNonNull(usuarioId, "usuarioId es obligatorio"),
                Objects.requireNonNull(categoria, "categoria es obligatoria"),
                requireNotBlank(asunto, "El asunto es obligatorio"), requireMensaje(mensaje), logCliente, adjunto,
                EstadoTicketSoporte.ABIERTO, null, null, now, now);
    }

    /** Solo para el adaptador de persistencia: reconstruye un ticket ya existente. */
    public static TicketSoporte rehydrate(TicketSoporteId id, UserId usuarioId, CategoriaSoporte categoria,
                                           String asunto, String mensaje, String logCliente, AdjuntoSoporte adjunto,
                                           EstadoTicketSoporte estado, String notasAdmin, Instant resueltoEn,
                                           Instant creadoEn, Instant actualizadoEn) {
        return new TicketSoporte(id, usuarioId, categoria, asunto, mensaje, logCliente, adjunto, estado, notasAdmin,
                resueltoEn, creadoEn, actualizadoEn);
    }

    public void resolver(String notasAdmin, Clock clock) {
        if (estado.estaResuelto()) {
            return;
        }
        this.notasAdmin = notasAdmin;
        this.estado = EstadoTicketSoporte.RESUELTO;
        this.resueltoEn = clock.now();
        this.actualizadoEn = this.resueltoEn;
    }

    private static String requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String requireMensaje(String mensaje) {
        String trimmed = requireNotBlank(mensaje, "El mensaje es obligatorio");
        if (trimmed.length() < 10) {
            throw new IllegalArgumentException("Cuentanos un poco mas - minimo 10 caracteres");
        }
        return trimmed;
    }

    @Override
    public String toString() {
        return "TicketSoporte[" + id + ", " + usuarioId + ", " + categoria + ", " + estado + "]";
    }
}
