package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.TipoDeFicha;
import com.renaser.os.onboarding.domain.model.pagos.ModalidadDePago;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.onboarding.domain.model.pagos.PagoDeLaPersona;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class EstadoOnboardingPersistenceMapper {

    EstadoOnboarding toDomain(EstadoOnboardingJpaEntity e) {
        return EstadoOnboarding.rehydrate(UserId.of(e.getUsuarioId()), e.getFlujoActual(), e.getSeccionActual(),
                e.getPasoActual() == null ? null : e.getPasoActual().intValue(), e.getProgresoFlujo(),
                e.getTerminosAceptadosEn(), e.getPactoAceptadoEn(), e.getPactoFirmadoEn(),
                e.getRocasSyncAceptadoEn(), e.getIniciadoEn(), e.getUltimaActividadEn(), e.isCompletado(),
                e.getCompletadoEn(), e.getCreadoEn(), e.getActualizadoEn(), TipoDeFicha.valueOf(e.getFicha()),
                e.getFichaAsignadaPor() == null ? null : UserId.of(e.getFichaAsignadaPor()),
                e.getFichaAsignadaEn(), e.getFichaVersion(), pagoDe(e));
    }

    private static PagoDeLaPersona pagoDe(EstadoOnboardingJpaEntity e) {
        return new PagoDeLaPersona(e.getPagoModalidad() == null ? null : ModalidadDePago.valueOf(e.getPagoModalidad()),
                e.getPagoMoneda() == null ? null : Moneda.valueOf(e.getPagoMoneda()), e.getCronogramaConfirmadoEn(),
                e.getCronogramaConfirmadoNombre(), e.getCronogramaConfirmadoHuella());
    }

    EstadoOnboardingJpaEntity toEntity(EstadoOnboarding d) {
        return new EstadoOnboardingJpaEntity(d.usuarioId().value(), d.flujoActual(), d.seccionActual(),
                d.pasoActual() == null ? null : d.pasoActual().shortValue(), d.progresoFlujo(),
                d.terminosAceptadosEn(), d.pactoAceptadoEn(), d.pactoFirmadoEn(), d.rocasSyncAceptadoEn(),
                d.iniciadoEn(), d.ultimaActividadEn(), d.completado(), d.completadoEn(), d.creadoEn(),
                d.actualizadoEn(), d.ficha().name(),
                d.fichaAsignadaPor() == null ? null : d.fichaAsignadaPor().value(), d.fichaAsignadaEn(),
                d.fichaVersion(), d.pago().modalidad() == null ? null : d.pago().modalidad().name(),
                d.pago().moneda() == null ? null : d.pago().moneda().name(), d.pago().confirmadoEn(), d.pago().confirmadoNombre(),
                d.pago().confirmadoHuella());
    }
}
