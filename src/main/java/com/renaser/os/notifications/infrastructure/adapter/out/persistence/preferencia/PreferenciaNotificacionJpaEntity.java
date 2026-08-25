package com.renaser.os.notifications.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion.TipoNotificacionJpa;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Tabla {@code preferencias_notificacion} (V1__baseline_renaser.sql:1352-1358) — PK natural
 * {@code (usuario_id, tipo)}, sin surrogate (P-28, comentario del baseline). */
@Entity
@Table(name = "preferencias_notificacion", schema = "renaser")
@IdClass(PreferenciaNotificacionId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferenciaNotificacionJpaEntity {

    @Id
    private UUID usuarioId;

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoNotificacionJpa tipo;

    private boolean habilitada;

    private Instant actualizadoEn;
}
