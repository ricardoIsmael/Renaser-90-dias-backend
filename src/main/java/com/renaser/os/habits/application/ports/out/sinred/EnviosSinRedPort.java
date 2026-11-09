package com.renaser.os.habits.application.ports.out.sinred;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Spec 048 (FR-008): los envíos de lo hecho sin red que ya llegaron, para registrar cada uno una sola vez.
 */
public interface EnviosSinRedPort {

    /** Si ese envío de esa persona ya llegó: cómo quedó ({@code motivoSinPuntos}, vacío si sumó sus puntos). */
    Optional<EnvioRegistrado> buscar(UUID idCliente, UserId participanteId);

    /** Lo recuerda. Si otro pedido con el mismo id ganó la carrera, no hace nada (el resultado ya es el mismo). */
    void registrar(UUID idCliente, UserId participanteId, UUID registroId, String motivoSinPuntos);

    record EnvioRegistrado(UUID registroId, String motivoSinPuntos) {
    }
}
