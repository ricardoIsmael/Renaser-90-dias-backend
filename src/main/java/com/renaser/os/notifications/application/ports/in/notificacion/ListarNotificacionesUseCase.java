package com.renaser.os.notifications.application.ports.in.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** La bandeja del que llama — autoservicio estricto, no hay parametro para pedir la de otro
 * (mismo diseno que {@code notifications/route.ts:GET} del repo viejo: el id sale siempre
 * del actor resuelto, nunca de la URL/query). */
public interface ListarNotificacionesUseCase {

    List<Notificacion> listar(UserId actorId);
}
