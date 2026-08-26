package com.renaser.os.habits.infrastructure.adapter.out.persistence.renombre;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.renombre.RenombreHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RenombreHabitoPersistenceMapper {

    RenombreHabito toDomain(RenombreHabitoJpaEntity e) {
        return RenombreHabito.rehydrate(UserId.of(e.getParticipanteId()), HabitoId.of(e.getHabitoId()),
                e.getTituloPersonal(), e.getMotivo(), e.getCreadoEn(), e.getActualizadoEn());
    }

    RenombreHabitoJpaEntity toEntity(RenombreHabito r) {
        return new RenombreHabitoJpaEntity(r.participanteId().value(), r.habitoId().value(), r.tituloPersonal(),
                r.motivo(), r.creadoEn(), r.actualizadoEn());
    }
}
