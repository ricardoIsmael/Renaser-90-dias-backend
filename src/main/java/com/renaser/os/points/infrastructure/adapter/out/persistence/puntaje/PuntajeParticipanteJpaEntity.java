package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "puntajes_participante", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PuntajeParticipanteJpaEntity {

    @Id
    private UUID participanteId;

    private BigDecimal coherencia;

    private Integer puntosLiga;

    private Short rachaActual;

    private Short rachaMaxima;

    private Instant actualizadoEn;
}
