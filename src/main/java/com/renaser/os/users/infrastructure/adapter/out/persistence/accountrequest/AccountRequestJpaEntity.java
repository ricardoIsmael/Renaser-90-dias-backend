package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Tabla `solicitudes_cuenta` (docs/db/sql/BD_NUEVA_V1.sql). */
@Entity
@Table(name = "solicitudes_cuenta", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountRequestJpaEntity {

    @Id
    private UUID id;

    private UUID supabaseUserId;

    private String email;

    private String nombreCompleto;

    private String telefono;

    private String ciudad;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoSolicitudJpa estado;

    private String motivoRechazo;

    private UUID revisadaPor;

    private Instant revisadaEn;

    private UUID usuarioCreadoId;

    /** inet en Postgres. Sin @JdbcTypeCode(SqlTypes.INET), Hibernate lo manda como varchar y Postgres lo rechaza. */
    @JdbcTypeCode(SqlTypes.INET)
    private String ipSolicitud;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
