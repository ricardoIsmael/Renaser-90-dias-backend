package com.renaser.os.notifications.application.ports.out.preferencia;

import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadPreferenciasPort {

    List<PreferenciaNotificacion> porUsuario(UserId usuarioId);

    /** Vacio = sin fila = default habilitada (ver {@code PreferenciaNotificacion.DEFAULT_HABILITADA}
     * y {@code chat/repository.ts:findNotificationEnabled} del repo viejo). Es el puerto que
     * consultan los listeners de evento antes de emitir (§ EmitirNotificacionUseCase). */
    Optional<Boolean> habilitadaPara(UserId usuarioId, TipoNotificacion tipo);
}
