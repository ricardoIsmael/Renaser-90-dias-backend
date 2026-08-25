package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketsoporte;

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
@Table(name = "tickets_soporte", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketSoporteJpaEntity {

    @Id
    private UUID id;

    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CategoriaSoporteJpa categoria;

    private String asunto;

    private String mensaje;

    private String logCliente;

    private String adjuntoBucket;

    private String adjuntoRuta;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoTicketSoporteJpa estado;

    private String notasAdmin;

    private Instant resueltoEn;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
