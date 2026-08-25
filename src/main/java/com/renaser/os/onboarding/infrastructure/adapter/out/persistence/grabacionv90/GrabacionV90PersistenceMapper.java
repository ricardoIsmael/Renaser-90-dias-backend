package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.grabacionv90;

import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class GrabacionV90PersistenceMapper {

    GrabacionV90 toDomain(GrabacionV90JpaEntity e) {
        return GrabacionV90.rehydrate(e.getId(), UserId.of(e.getUsuarioId()), e.getFase(), e.getEje(),
                e.getIndice(), e.getClavePregunta(), e.isGrabada(), e.getMediaId(), e.getDuracionSegundos(),
                e.getTranscripcion(), toDomainEstado(e.getEstadoIa()), e.getIntentosIa(), e.getFeedbackIa(),
                e.getGrabadaEn(), e.getCreadoEn(), e.getActualizadoEn());
    }

    GrabacionV90JpaEntity toEntity(GrabacionV90 g) {
        return new GrabacionV90JpaEntity(g.id(), g.usuarioId().value(), g.fase(), g.eje(), g.indice(),
                g.clavePregunta(), g.grabada(), g.mediaId(), g.duracionSegundos(), g.transcripcion(),
                toJpaEstado(g.estadoIa()), g.intentosIa(), g.feedbackIa(), g.grabadaEn(), g.creadoEn(),
                g.actualizadoEn());
    }

    private EstadoIAv90Jpa toJpaEstado(EstadoIAv90 estado) {
        return switch (estado) {
            case PENDIENTE -> EstadoIAv90Jpa.PENDIENTE;
            case PROCESANDO -> EstadoIAv90Jpa.PROCESANDO;
            case APROBADA -> EstadoIAv90Jpa.APROBADA;
            case RECHAZADA -> EstadoIAv90Jpa.RECHAZADA;
            case REVISION_MANUAL -> EstadoIAv90Jpa.REVISION_MANUAL;
        };
    }

    private EstadoIAv90 toDomainEstado(EstadoIAv90Jpa jpa) {
        return switch (jpa) {
            case PENDIENTE -> EstadoIAv90.PENDIENTE;
            case PROCESANDO -> EstadoIAv90.PROCESANDO;
            case APROBADA -> EstadoIAv90.APROBADA;
            case RECHAZADA -> EstadoIAv90.RECHAZADA;
            case REVISION_MANUAL -> EstadoIAv90.REVISION_MANUAL;
        };
    }
}
