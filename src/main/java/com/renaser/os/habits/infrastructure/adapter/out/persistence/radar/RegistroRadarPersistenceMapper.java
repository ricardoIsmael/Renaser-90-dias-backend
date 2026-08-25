package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RegistroRadarPersistenceMapper {

    RegistroRadar toDomain(RegistroRadarJpaEntity e) {
        return RegistroRadar.rehydrate(RegistroRadarId.of(e.getId()), UserId.of(e.getParticipanteId()),
                e.getQueHago(), e.getQuePienso(), e.getQueSiento(), e.getNivelEnergia(), e.getQueEvito(),
                e.getCreadoEn());
    }

    RegistroRadarJpaEntity toEntity(RegistroRadar r) {
        return new RegistroRadarJpaEntity(r.id().value(), r.participanteId().value(), r.queHago(), r.quePienso(),
                r.queSiento(), (short) r.nivelEnergia(), r.queEvito(), r.creadoEn());
    }
}
