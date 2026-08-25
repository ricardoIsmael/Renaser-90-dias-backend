package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketmentor;

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
@Table(name = "tickets_mentor", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketMentorJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    private String descripcionBloqueo;

    private String solucionesIntentadas;

    private String impactoMetaSmart;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoTicketMentorJpa estado;

    private String respuestaMentor;

    private Instant respondidoEn;

    private boolean guardadoEnBiblioteca;

    private Instant creadoEn;
}
