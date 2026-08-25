package com.renaser.os.notifications.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion.TipoNotificacionJpa;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Clase de PK compuesta para {@link PreferenciaNotificacionJpaEntity} (usuario_id, tipo). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferenciaNotificacionId implements Serializable {

    private UUID usuarioId;
    private TipoNotificacionJpa tipo;
}
