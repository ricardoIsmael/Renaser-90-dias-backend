package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

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

@Entity
@Table(name = "publicaciones_muro", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicacionJpaEntity {

    @Id
    private UUID id;

    private UUID autorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoPublicacionJpa tipo;

    private String categoriaClave;

    private String texto;

    private boolean oculta;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
