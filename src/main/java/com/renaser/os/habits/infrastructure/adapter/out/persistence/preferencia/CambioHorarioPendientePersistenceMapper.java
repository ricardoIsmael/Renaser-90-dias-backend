package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class CambioHorarioPendientePersistenceMapper {

    CambioHorarioPendiente toDomain(CambioHorarioPendienteJpaEntity e) {
        return CambioHorarioPendiente.rehydrate(UserId.of(e.getParticipanteId()), HabitoId.of(e.getHabitoId()),
                e.getHoraDisparo(), e.getHoraLimite(), e.getRecordatorioActivo(),
                e.getMinutosRecordatorio() != null ? e.getMinutosRecordatorio().intValue() : null,
                e.getFechaEfectiva(), e.getCreadoEn());
    }

    CambioHorarioPendienteJpaEntity toEntity(CambioHorarioPendiente c) {
        return new CambioHorarioPendienteJpaEntity(c.participanteId().value(), c.habitoId().value(),
                c.horaDisparo(), c.horaLimite(), c.recordatorioActivo(),
                c.minutosRecordatorio() != null ? c.minutosRecordatorio().shortValue() : null, c.fechaEfectiva(),
                c.creadoEn());
    }
}
