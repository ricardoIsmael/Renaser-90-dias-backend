package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import org.springframework.stereotype.Component;

@Component
class GuiaHabitoPersistenceMapper {

    GuiaHabito toDomain(GuiaHabitoJpaEntity e) {
        return GuiaHabito.rehydrate(GuiaHabitoId.of(e.getId()), HabitoId.of(e.getHabitoId()), e.getDiaInicio(),
                e.getDiaFin() != null ? e.getDiaFin().intValue() : null, e.getQueHacer(), e.getComoHacerlo(),
                e.getCiencia(), e.getRenaser(), e.getAlquimia(), e.getResultados(), e.getMantraTitulo(),
                e.getMantraIntro(), e.getMantraCuerpo(), e.getReferenciaFuente(), e.getCreadoEn(),
                e.getActualizadoEn());
    }

    GuiaHabitoJpaEntity toEntity(GuiaHabito g) {
        return new GuiaHabitoJpaEntity(g.id().value(), g.habitoId().value(), (short) g.diaInicio(),
                g.diaFin() != null ? g.diaFin().shortValue() : null, g.queHacer(), g.comoHacerlo(), g.ciencia(),
                g.renaser(), g.alquimia(), g.resultados(), g.mantraTitulo(), g.mantraIntro(), g.mantraCuerpo(),
                g.referenciaFuente(), g.creadoEn(), g.actualizadoEn());
    }
}
