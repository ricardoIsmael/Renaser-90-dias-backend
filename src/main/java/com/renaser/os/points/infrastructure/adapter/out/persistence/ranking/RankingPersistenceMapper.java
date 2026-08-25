package com.renaser.os.points.infrastructure.adapter.out.persistence.ranking;

import com.renaser.os.points.domain.model.ranking.PosicionRanking;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RankingPersistenceMapper {

    RankingAprendizJpaEntity toEntity(PosicionRanking p) {
        return new RankingAprendizJpaEntity(p.fecha(), toJpaTipo(p.tipo()), p.participanteId().value(),
                p.posicion(), p.puntaje());
    }

    TipoRankingJpa toJpaTipo(TipoRanking tipo) {
        return switch (tipo) {
            case GENERAL -> TipoRankingJpa.GENERAL;
            case COHORT -> TipoRankingJpa.COHORTE;
            case CELL -> TipoRankingJpa.CELULA;
            case LEAGUE -> TipoRankingJpa.LIGA;
        };
    }

    UserId participanteIdDe(RankingAprendizJpaEntity e) {
        return UserId.of(e.getParticipanteId());
    }
}
