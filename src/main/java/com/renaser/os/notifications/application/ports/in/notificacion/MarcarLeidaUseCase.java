package com.renaser.os.notifications.application.ports.in.notificacion;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Marca una notificacion como leida. Idempotente. El id ajeno y el inexistente responden
 * IGUAL ({@link NoSuchElementException} -> 404 via {@code GlobalExceptionHandler}) a
 * proposito: distinguirlos revelaria que ids existen (mismo criterio que
 * {@code notifications/service.ts:markAsRead} del repo viejo).
 */
public interface MarcarLeidaUseCase {

    ResultadoLectura marcarLeida(UserId actorId, Long notificacionId);

    record ResultadoLectura(Long id, Instant leidaEn) {
    }
}
