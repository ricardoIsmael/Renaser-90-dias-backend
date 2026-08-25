package com.renaser.os.notifications.application.ports.out.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

public interface SaveNotificacionPort {

    Notificacion guardar(Notificacion notificacion);

    /** UPDATE atomico filtrado por (id, usuarioId, no leida) — nunca "cargar y comparar despues"
     * (mismo patron que {@code notifications/repository.ts:markRead}: una carrera no puede tocar
     * la notificacion de otro). Devuelve 1 si movio {@code leidaEn}, 0 si ya estaba leida, no
     * existe o no es del usuario. */
    int marcarLeida(Long id, UserId usuarioId, Instant ahora);

    /** Vacia el badge completo. Devuelve cuantas filas cambiaron (0 es una respuesta valida). */
    int marcarTodasLeidas(UserId usuarioId, Instant ahora);

    /** Retencion (CLAUDE.MD, `docs/PLAN_DE_MODULOS.md` §5): purga filas mas viejas que {@code limite}. */
    int purgarAnterioresA(Instant limite);
}
