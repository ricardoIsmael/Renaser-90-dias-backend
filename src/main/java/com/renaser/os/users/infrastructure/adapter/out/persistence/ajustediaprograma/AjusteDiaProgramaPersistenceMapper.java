package com.renaser.os.users.infrastructure.adapter.out.persistence.ajustediaprograma;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;
import org.springframework.stereotype.Component;

/** Mapper a mano (D-28): los cuatro contadores son `smallint` (short) contra el `int` del
 * dominio, y los UUID crudos se envuelven en `UserId` — no es mapeo plano. */
@Component
class AjusteDiaProgramaPersistenceMapper {

    AjusteDiaPrograma toDomain(AjusteDiaProgramaJpaEntity e) {
        return AjusteDiaPrograma.rehydrate(
                e.getId(),
                UserId.of(e.getParticipanteId()),
                e.getDiaAnterior(),
                e.getDiaNuevo(),
                e.getDiasAjusteAnterior(),
                e.getDiasAjusteNuevo(),
                e.getMotivo(),
                UserId.of(e.getAjustadoPor()),
                e.getAjustadoEn());
    }

    AjusteDiaProgramaJpaEntity toEntity(AjusteDiaPrograma a) {
        return new AjusteDiaProgramaJpaEntity(
                a.id(),
                a.participanteId().value(),
                (short) a.diaAnterior(),
                (short) a.diaNuevo(),
                (short) a.diasAjusteAnterior(),
                (short) a.diasAjusteNuevo(),
                a.motivo(),
                a.ajustadoPor().value(),
                a.ajustadoEn());
    }
}
