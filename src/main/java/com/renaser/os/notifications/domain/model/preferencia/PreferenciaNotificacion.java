package com.renaser.os.notifications.domain.model.preferencia;

import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Preferencia de un usuario para un tipo de notificacion (tabla {@code preferencias_notificacion},
 * PK natural {@code (usuario_id, tipo)} — sin id propio, ver V1__baseline_renaser.sql:1352-1358).
 *
 * <p>Regla extraida literal de {@code chat/repository.ts:findNotificationEnabled} (repo viejo):
 * sin fila para {@code (usuario, tipo)}, el default es HABILITADA — nunca se asume apagado por
 * ausencia. Es un {@code record}, no una clase con setters: "cambiar" la preferencia es
 * reemplazar la fila entera (mismo patron que el UPSERT del repositorio viejo).
 */
public record PreferenciaNotificacion(UserId usuarioId, TipoNotificacion tipo, boolean habilitada,
                                       Instant actualizadoEn) {

    public static final boolean DEFAULT_HABILITADA = true;

    public PreferenciaNotificacion {
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn es obligatorio");
    }

    public static PreferenciaNotificacion de(UserId usuarioId, TipoNotificacion tipo, boolean habilitada,
                                              Clock clock) {
        return new PreferenciaNotificacion(usuarioId, tipo, habilitada, clock.now());
    }
}
