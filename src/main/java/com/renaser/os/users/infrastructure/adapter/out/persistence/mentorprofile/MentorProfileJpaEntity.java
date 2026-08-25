package com.renaser.os.users.infrastructure.adapter.out.persistence.mentorprofile;

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

/** Tabla `perfiles_mentor` (docs/db/sql/BD_NUEVA_V1.sql). Unico perfil con tabla propia (D-25). */
@Entity
@Table(name = "perfiles_mentor", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfileJpaEntity {

    @Id
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private NivelMentorJpa nivel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoOperativoJpa estadoOperativo;

    private String bio;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
