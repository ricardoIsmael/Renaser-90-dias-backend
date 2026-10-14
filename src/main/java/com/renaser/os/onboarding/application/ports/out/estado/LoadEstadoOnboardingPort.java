package com.renaser.os.onboarding.application.ports.out.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;
import java.util.Set;

public interface LoadEstadoOnboardingPort {

    Optional<EstadoOnboarding> deUsuario(UserId usuarioId);

    /**
     * Dashboard admin de onboarding (gap #8 de docs/PLAN_INTEGRACION_FRONTEND.md): agregado de `estado_onboarding`.
     *
     * @param sinContar las cuentas que no cuentan: las de prueba de «ver como» (D-270), que tienen su onboarding
     *                  completo para que la app las muestre como a cualquier aprendiz
     */
    ResumenEstadosOnboarding contarResumen(Set<UserId> sinContar);

    record ResumenEstadosOnboarding(long totalIniciados, long totalCompletados, long totalPactoFirmado) {
    }
}
