package com.renaser.os.chat.application.ports.out.bienvenida;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quién espera su bienvenida en el chat de su grupo estable, y la marca (D-191). La marca vive en
 * {@code community} ({@code asignaciones_celula.bienvenida_enviada_en}, V71); el chat solo pregunta.
 */
public interface BienvenidaEnGrupoPort {

    /** Vacío si el grupo es la recepción, no está operativo o no tiene mentor vigente. */
    Optional<Pendientes> pendientes(UUID celulaId);

    /**
     * Se llama en la MISMA transacción que guarda el mensaje.
     *
     * @return {@code true} si esta llamada dejó la marca; {@code false} si ya estaba (no mandar)
     */
    boolean marcarDada(UUID asignacionId);

    record Pendientes(UserId mentorId, List<Pendiente> aprendices) {
    }

    /** @param desde cuándo empezó esa pertenencia ({@code asignaciones_celula.inicio}): sin bienvenidas atrasadas (D-204) */
    record Pendiente(UUID asignacionId, UserId aprendizId, Instant desde) {
    }
}
