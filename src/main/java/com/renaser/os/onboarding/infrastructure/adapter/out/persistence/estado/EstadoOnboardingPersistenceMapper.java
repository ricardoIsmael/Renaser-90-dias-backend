package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class EstadoOnboardingPersistenceMapper {

    EstadoOnboarding toDomain(EstadoOnboardingJpaEntity e) {
        return EstadoOnboarding.rehydrate(UserId.of(e.getUsuarioId()), e.getFlujoActual(), e.getSeccionActual(),
                e.getPasoActual() == null ? null : e.getPasoActual().intValue(), e.getProgresoFlujo(),
                e.getTerminosAceptadosEn(), e.getPactoAceptadoEn(), e.getPactoFirmadoEn(),
                e.getRocasSyncAceptadoEn(), e.getIniciadoEn(), e.getUltimaActividadEn(), e.isCompletado(),
                e.getCompletadoEn(), e.getCreadoEn(), e.getActualizadoEn());
    }

    EstadoOnboardingJpaEntity toEntity(EstadoOnboarding d) {
        return new EstadoOnboardingJpaEntity(d.usuarioId().value(), d.flujoActual(), d.seccionActual(),
                d.pasoActual() == null ? null : d.pasoActual().shortValue(), d.progresoFlujo(),
                d.terminosAceptadosEn(), d.pactoAceptadoEn(), d.pactoFirmadoEn(), d.rocasSyncAceptadoEn(),
                d.iniciadoEn(), d.ultimaActividadEn(), d.completado(), d.completadoEn(), d.creadoEn(),
                d.actualizadoEn());
    }
}
