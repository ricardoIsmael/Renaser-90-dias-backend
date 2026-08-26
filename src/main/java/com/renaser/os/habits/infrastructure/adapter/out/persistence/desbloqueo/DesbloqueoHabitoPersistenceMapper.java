package com.renaser.os.habits.infrastructure.adapter.out.persistence.desbloqueo;

import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class DesbloqueoHabitoPersistenceMapper {

    DesbloqueoHabito toDomain(DesbloqueoHabitoJpaEntity e) {
        return DesbloqueoHabito.rehydrate(UserId.of(e.getParticipanteId()), HabitoId.of(e.getHabitoId()),
                e.getDiaDesbloqueo().intValue(), e.getElegidoEn(), e.getCreadoEn(), e.getActualizadoEn());
    }
}
