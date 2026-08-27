package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort.ResumenEstadosOnboarding;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.LoadGrabacionV90Port;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Dashboard admin de onboarding (gap #8). Tests de autorizacion negativa (CLAUDE.MD §0.3). */
@ExtendWith(MockitoExtension.class)
class OnboardingDashboardServiceTest {

    @Mock
    private LoadEstadoOnboardingPort loadEstadoOnboardingPort;
    @Mock
    private LoadGrabacionV90Port loadGrabacionV90Port;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ParticipacionProgramaFinder participacionProgramaFinder;

    private OnboardingDashboardService service;

    @BeforeEach
    void setUp() {
        service = new OnboardingDashboardService(loadEstadoOnboardingPort, loadGrabacionV90Port, userSummaryFinder,
                participacionProgramaFinder);
        lenient().when(loadGrabacionV90Port.contarPorEstado(any())).thenReturn(0L);
    }

    private static UserId id() {
        return UserId.of(UUID.randomUUID());
    }

    private static UserSummary resumen(UserId id, UserRole role, UserStatus status) {
        return new UserSummary(id, "Fixture", null, role, status);
    }

    @Test
    @DisplayName("un actor inexistente recibe 404")
    void rechazaActorInexistente() {
        UserId actorId = id();
        when(userSummaryFinder.findById(actorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerResumen(actorId)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("un actor SUSPENDIDO recibe 403 aunque su rol sea ADMIN")
    void rechazaActorSuspendido() {
        UserId actorId = id();
        when(userSummaryFinder.findById(actorId))
                .thenReturn(Optional.of(resumen(actorId, UserRole.ADMIN, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.obtenerResumen(actorId)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un MENTOR activo (rol sin permiso) recibe 403")
    void rechazaRolSinPermiso() {
        UserId actorId = id();
        when(userSummaryFinder.findById(actorId))
                .thenReturn(Optional.of(resumen(actorId, UserRole.MENTOR, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.obtenerResumen(actorId)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un ADMIN activo si puede consultar el dashboard, con los contadores agregados")
    void aceptaAdminActivoYAgregaLosContadores() {
        UserId actorId = id();
        when(userSummaryFinder.findById(actorId))
                .thenReturn(Optional.of(resumen(actorId, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(loadEstadoOnboardingPort.contarResumen()).thenReturn(new ResumenEstadosOnboarding(10, 4, 6));
        when(loadGrabacionV90Port.contarPorEstado(EstadoIAv90.REVISION_MANUAL)).thenReturn(3L);
        when(loadGrabacionV90Port.contarPorEstado(EstadoIAv90.APROBADA)).thenReturn(1L);
        when(participacionProgramaFinder.usuariosActivosConRol(Set.of(UserRole.TRAINEE)))
                .thenReturn(List.of(id(), id(), id()));

        var resumen = service.obtenerResumen(actorId);

        assertThat(resumen.totalAprendicesActivos()).isEqualTo(3);
        assertThat(resumen.totalOnboardingIniciado()).isEqualTo(10);
        assertThat(resumen.totalOnboardingCompletado()).isEqualTo(4);
        assertThat(resumen.totalPactoFase1Firmado()).isEqualTo(6);
        assertThat(resumen.grabacionesRevisionManual()).isEqualTo(3);
        assertThat(resumen.grabacionesAprobadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("un ALCHEMIST activo tambien puede consultar el dashboard")
    void aceptaAlchemistActivo() {
        UserId actorId = id();
        when(userSummaryFinder.findById(actorId))
                .thenReturn(Optional.of(resumen(actorId, UserRole.ALCHEMIST, UserStatus.ACTIVE)));
        when(loadEstadoOnboardingPort.contarResumen()).thenReturn(new ResumenEstadosOnboarding(0, 0, 0));
        when(participacionProgramaFinder.usuariosActivosConRol(Set.of(UserRole.TRAINEE))).thenReturn(List.of());

        assertThat(service.obtenerResumen(actorId)).isNotNull();
    }
}
