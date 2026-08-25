package com.renaser.os.notifications.infrastructure.adapter.out.persistence.tokenpush;

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

/** Tabla {@code tokens_push} (V1__baseline_renaser.sql:1342-1350). {@code token} es UNIQUE
 * a nivel de esquema — la unicidad real la impone Postgres, no esta entidad. */
@Entity
@Table(name = "tokens_push", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPushJpaEntity {

    @Id
    private UUID id;

    private UUID usuarioId;

    private String token;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PlataformaPushJpa plataforma;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
