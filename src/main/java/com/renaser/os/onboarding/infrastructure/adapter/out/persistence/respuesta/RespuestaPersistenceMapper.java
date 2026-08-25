package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RespuestaPersistenceMapper {

    Respuesta toDomain(RespuestaOnboardingJpaEntity e) {
        return Respuesta.rehydrate(e.getId(), UserId.of(e.getUsuarioId()), e.getPreguntaId(), e.getValorTexto(),
                e.getValorNumero(), e.getValorBooleano(), e.getValorEscala(), e.getValorJson(), e.getMediaId(),
                e.getAceptadaEn(), e.getRespondidaEn(), e.getActualizadoEn());
    }

    RespuestaOnboardingJpaEntity toEntity(Respuesta r) {
        return new RespuestaOnboardingJpaEntity(r.id(), r.usuarioId().value(), r.preguntaId(), r.valorTexto(),
                r.valorNumero(), r.valorBooleano(), r.valorEscala(), r.valorJson(), r.mediaId(), r.aceptadaEn(),
                r.respondidaEn(), r.actualizadoEn());
    }
}
