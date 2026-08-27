package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.admin.OnboardingDashboardUseCase;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.LoadGrabacionV90Port;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Dashboard admin de onboarding (gap #8). El gate de rol se resuelve directo contra
 * {@code users.api.UserSummaryFinder} (nunca contra {@code onboarding.domain}/
 * {@code onboarding.application} de otro modulo) — ver javadoc de
 * {@link OnboardingDashboardUseCase} sobre por que no se reusa
 * {@code ConsultarActorPort} de este mismo modulo.
 */
@Service
class OnboardingDashboardService implements OnboardingDashboardUseCase {

    private final LoadEstadoOnboardingPort loadEstadoOnboardingPort;
    private final LoadGrabacionV90Port loadGrabacionV90Port;
    private final UserSummaryFinder userSummaryFinder;
    private final ParticipacionProgramaFinder participacionProgramaFinder;

    OnboardingDashboardService(LoadEstadoOnboardingPort loadEstadoOnboardingPort,
                               LoadGrabacionV90Port loadGrabacionV90Port, UserSummaryFinder userSummaryFinder,
                               ParticipacionProgramaFinder participacionProgramaFinder) {
        this.loadEstadoOnboardingPort = loadEstadoOnboardingPort;
        this.loadGrabacionV90Port = loadGrabacionV90Port;
        this.userSummaryFinder = userSummaryFinder;
        this.participacionProgramaFinder = participacionProgramaFinder;
    }

    @Override
    public ResumenOnboardingAdmin obtenerResumen(UserId actorId) {
        requireAdminActivo(actorId);

        var resumenEstados = loadEstadoOnboardingPort.contarResumen();
        long totalAprendices = participacionProgramaFinder.usuariosActivosConRol(Set.of(UserRole.TRAINEE)).size();

        return new ResumenOnboardingAdmin(
                totalAprendices,
                resumenEstados.totalIniciados(),
                resumenEstados.totalCompletados(),
                resumenEstados.totalPactoFirmado(),
                loadGrabacionV90Port.contarPorEstado(EstadoIAv90.PENDIENTE),
                loadGrabacionV90Port.contarPorEstado(EstadoIAv90.PROCESANDO),
                loadGrabacionV90Port.contarPorEstado(EstadoIAv90.REVISION_MANUAL),
                loadGrabacionV90Port.contarPorEstado(EstadoIAv90.APROBADA),
                loadGrabacionV90Port.contarPorEstado(EstadoIAv90.RECHAZADA));
    }

    /** Fail-closed (docs/BITACORA_ERRORES.md E-42): sin recurso previo por id que proteger
     * (es un agregado global), asi que no hay orden que respetar, pero el chequeo de
     * "existe y tiene permiso" sigue siendo un solo booleano, nunca dos excepciones distintas. */
    private void requireAdminActivo(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (!actor.status().allowsAccess() || (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST)) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST consultan el dashboard de onboarding");
        }
    }
}
