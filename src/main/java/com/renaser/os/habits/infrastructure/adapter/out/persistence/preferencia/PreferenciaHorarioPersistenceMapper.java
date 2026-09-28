package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
class PreferenciaHorarioPersistenceMapper {

    PreferenciaHorario toDomain(PreferenciaHorarioJpaEntity e) {
        return PreferenciaHorario.rehydrate(UserId.of(e.getParticipanteId()), HabitoId.of(e.getHabitoId()),
                e.getHoraDisparo(), e.getHoraLimite(), e.isRecordatorioActivo(),
                e.getMinutosRecordatorio() != null ? e.getMinutosRecordatorio().intValue() : null,
                aLista(e.getAntelacionesRecordatorio()), e.getCreadoEn(), e.getActualizadoEn());
    }

    PreferenciaHorarioJpaEntity toEntity(PreferenciaHorario p) {
        return new PreferenciaHorarioJpaEntity(p.participanteId().value(), p.habitoId().value(), p.horaDisparo(),
                p.horaLimite(), p.recordatorioActivo(),
                p.minutosRecordatorio() != null ? p.minutosRecordatorio().shortValue() : null,
                aArreglo(p.antelacionesRecordatorio()), p.creadoEn(), p.actualizadoEn());
    }

    /** {@code smallint[]} ↔ lista de minutos (D-217). {@code null} se conserva: es «no se conocen». */
    static List<Integer> aLista(Short[] antelaciones) {
        return antelaciones == null ? null : Arrays.stream(antelaciones).map(Short::intValue).toList();
    }

    static Short[] aArreglo(List<Integer> antelaciones) {
        return antelaciones == null ? null : antelaciones.stream().map(Integer::shortValue).toArray(Short[]::new);
    }
}
