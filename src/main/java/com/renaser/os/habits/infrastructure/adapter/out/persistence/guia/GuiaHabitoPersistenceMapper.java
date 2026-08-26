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
}
