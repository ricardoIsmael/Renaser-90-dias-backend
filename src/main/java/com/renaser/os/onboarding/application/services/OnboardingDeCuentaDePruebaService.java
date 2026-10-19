package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.OnboardingDeCuentaDePruebaUseCase;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** El onboarding completo de una cuenta de prueba de «ver como» (ver {@link OnboardingDeCuentaDePruebaUseCase}; D-273). */
@Service
class OnboardingDeCuentaDePruebaService implements OnboardingDeCuentaDePruebaUseCase {

    private final LoadEstadoOnboardingPort loadEstado;
    private final SaveEstadoOnboardingPort saveEstado;
    private final UserSummaryFinder usuarios;
    private final Clock clock;

    OnboardingDeCuentaDePruebaService(LoadEstadoOnboardingPort loadEstado, SaveEstadoOnboardingPort saveEstado,
                                      UserSummaryFinder usuarios, Clock clock) {
        this.loadEstado = loadEstado;
        this.saveEstado = saveEstado;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void completar(UserId soporteTi, UserId cuentaDePrueba) {
        boolean esSoporteTiActivo = soporteTi != null && usuarios.findById(soporteTi)
                .filter(u -> u.role() == UserRole.IT_SUPPORT && u.status() == UserStatus.ACTIVE).isPresent();
        if (!esSoporteTiActivo) {
            throw new NotAuthorizedException("Solo Soporte TI prepara una cuenta de prueba");
        }
        boolean esDePrueba = cuentaDePrueba != null && usuarios.findById(cuentaDePrueba)
                .map(UserSummary::esCuentaDePrueba).orElse(false);
        if (!esDePrueba) {
            throw new IllegalArgumentException("Solo se completa el onboarding de una cuenta de prueba");
        }
        EstadoOnboarding estado = loadEstado.deUsuario(cuentaDePrueba)
                .orElseGet(() -> EstadoOnboarding.iniciar(cuentaDePrueba, clock));
        if (estado.completado() && estado.pactoFirmadoEn() != null) {
            return;
        }
        if (estado.terminosAceptadosEn() == null) {
            estado.aceptarHito(HitoOnboarding.TERMINOS, clock);
        }
        if (estado.pactoAceptadoEn() == null) {
            estado.aceptarHito(HitoOnboarding.PACTO, clock);
        }
        if (estado.pactoFirmadoEn() == null) {
            estado.aceptarHito(HitoOnboarding.PACTO_FIRMADO, clock);
        }
        if (estado.rocasSyncAceptadoEn() == null) {
            estado.aceptarHito(HitoOnboarding.ROCAS_SYNC, clock);
        }
        estado.marcarCompletado(clock);
        saveEstado.guardar(estado);
    }
}
