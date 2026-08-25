package com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Tabla {@code notificaciones} (V1__baseline_renaser.sql:1360-1370) — log de alto volumen,
 * PK bigint IDENTITY. */
@Entity
@Table(name = "notificaciones", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoNotificacionJpa tipo;

    private String titulo;

    private String cuerpo;

    private String rutaApp;

    private Instant leidaEn;

    private Instant creadoEn;
}
