package com.renaser.os.calendar.infrastructure.adapter.out.persistence.confirmacion;

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

/** Sin id propio — PK compuesta (evento_id, inicio_ocurrencia, usuario_id), P-28 del baseline. */
@Entity
@Table(name = "confirmaciones_evento", schema = "renaser")
@IdClass(ConfirmacionEventoId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmacionEventoJpaEntity {

    @Id
    private UUID eventoId;

    @Id
    private Instant inicioOcurrencia;

    @Id
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoConfirmacionJpa estado;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
