package com.renaser.os.habits.infrastructure.adapter.out.persistence.eleccion;

import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class EleccionDiaSemanalPersistenceMapper {

    EleccionDiaSemanal toDomain(EleccionDiaSemanalJpaEntity e) {
        return EleccionDiaSemanal.rehydrate(UserId.of(e.getParticipanteId()), HabitoId.of(e.getHabitoId()),
                e.getFechaEjecucion(), e.getSemanaInicio(), e.getCreadoEn());
    }

    EleccionDiaSemanalJpaEntity toEntity(EleccionDiaSemanal d) {
        return new EleccionDiaSemanalJpaEntity(d.participanteId().value(), d.habitoId().value(), d.fechaEjecucion(),
                d.semanaInicio(), d.creadoEn());
    }
}
