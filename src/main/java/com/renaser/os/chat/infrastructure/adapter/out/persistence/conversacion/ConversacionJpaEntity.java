package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

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
@Table(name = "conversaciones", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversacionJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoConversacionJpa tipo;

    /** `celulas.id` de `community` — UUID plano, nunca relacion JPA hacia otro modulo. */
    private UUID celulaId;

    private String claveDirecta;

    private String nombre;

    private Instant creadoEn;
}
