package com.renaser.os.points.infrastructure.adapter.out.persistence.ranking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/** Clase de PK compuesta para RankingAprendizJpaEntity (fecha, tipo, participante_id). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RankingAprendizId implements Serializable {

    private LocalDate fecha;
    private TipoRankingJpa tipo;
    private UUID participanteId;
}
