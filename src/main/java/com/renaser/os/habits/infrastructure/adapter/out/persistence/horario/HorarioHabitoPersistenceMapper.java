package com.renaser.os.habits.infrastructure.adapter.out.persistence.horario;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import org.springframework.stereotype.Component;

@Component
class HorarioHabitoPersistenceMapper {

    HorarioHabito toDomain(HorarioHabitoJpaEntity e) {
        return HorarioHabito.rehydrate(HorarioHabitoId.of(e.getId()), HabitoId.of(e.getHabitoId()), e.getDiaInicio(),
                e.getDiaFin() != null ? e.getDiaFin().intValue() : null, toDomainTipoDia(e.getTipoDia()),
                e.getHoraDisparo(), e.getHoraLimite(), e.getCreadoEn(), e.getActualizadoEn());
    }

    HorarioHabitoJpaEntity toEntity(HorarioHabito h) {
        return new HorarioHabitoJpaEntity(h.id().value(), h.habitoId().value(), (short) h.diaInicio(),
                h.diaFin() != null ? h.diaFin().shortValue() : null, toJpaTipoDia(h.tipoDia()), h.horaDisparo(),
                h.horaLimite(), h.creadoEn(), h.actualizadoEn());
    }

    private TipoDiaJpa toJpaTipoDia(TipoDia t) {
        return switch (t) {
            case DISCIPLINA -> TipoDiaJpa.DISCIPLINA;
            case INTOXICACION -> TipoDiaJpa.INTOXICACION;
            case TODOS -> TipoDiaJpa.TODOS;
            case DOMINGO -> TipoDiaJpa.DOMINGO;
        };
    }

    private TipoDia toDomainTipoDia(TipoDiaJpa t) {
        return switch (t) {
            case DISCIPLINA -> TipoDia.DISCIPLINA;
            case INTOXICACION -> TipoDia.INTOXICACION;
            case TODOS -> TipoDia.TODOS;
            case DOMINGO -> TipoDia.DOMINGO;
        };
    }
}
