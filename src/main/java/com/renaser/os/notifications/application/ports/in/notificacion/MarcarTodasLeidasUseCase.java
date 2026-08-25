package com.renaser.os.notifications.application.ports.in.notificacion;

import com.renaser.os.shared.domain.UserId;

/** Vacia el badge. Idempotente: sin no leidas devuelve 0 y sigue siendo una respuesta valida. */
public interface MarcarTodasLeidasUseCase {

    int marcarTodas(UserId actorId);
}
