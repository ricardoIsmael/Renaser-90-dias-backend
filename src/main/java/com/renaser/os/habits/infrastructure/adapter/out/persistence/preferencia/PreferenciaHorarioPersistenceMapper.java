package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class PreferenciaHorarioPersistenceMapper {

    PreferenciaHorario toDomain(PreferenciaHorarioJpaEntity e) {
        return PreferenciaHorario.rehydrate(UserId.of(e.getParticipanteId()), HabitoId.of(e.getHabitoId()),
                e.getHoraDisparo(), e.getHoraLimite(), e.isRecordatorioActivo(),
                e.getMinutosRecordatorio() != null ? e.getMinutosRecordatorio().intValue() : null, e.getCreadoEn(),
                e.getActualizadoEn());
    }

    PreferenciaHorarioJpaEntity toEntity(PreferenciaHorario p) {
        return new PreferenciaHorarioJpaEntity(p.participanteId().value(), p.habitoId().value(), p.horaDisparo(),
                p.horaLimite(), p.recordatorioActivo(),
                p.minutosRecordatorio() != null ? p.minutosRecordatorio().shortValue() : null, p.creadoEn(),
                p.actualizadoEn());
    }
}
