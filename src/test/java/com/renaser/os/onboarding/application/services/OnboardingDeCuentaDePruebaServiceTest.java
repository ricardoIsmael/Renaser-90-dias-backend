package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** El onboarding completo de una cuenta de prueba de «ver como» (D-273). Sin Spring. */
@ExtendWith(MockitoExtension.class)
class OnboardingDeCuentaDePruebaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-14T15:00:00Z"));

    @Mock
    private LoadEstadoOnboardingPort loadEstado;
    @Mock
    private SaveEstadoOnboardingPort saveEstado;
    @Mock
    private UserSummaryFinder usuarios;

    private OnboardingDeCuentaDePruebaService servicio;
    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId aprendiz = UserId.of(UUID.randomUUID());

    @BeforeEach
    void armar() {
        servicio = new OnboardingDeCuentaDePruebaService(loadEstado, saveEstado, usuarios, CLOCK);
        lenient().when(usuarios.findById(soporte)).thenReturn(Optional.of(new UserSummary(soporte, "Soporte", null,
                UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
    }

    private void esCuentaDePrueba(UserId id, boolean deprueba) {
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Prueba · Aprendiz", null,
                UserRole.TRAINEE, UserStatus.ACTIVE, null, deprueba)));
    }

    @Test
    @DisplayName("deja términos, pacto aceptado y firmado, rocas y completado: la app muestra su inicio")
    void loCompleta() {
        esCuentaDePrueba(aprendiz, true);
        when(loadEstado.deUsuario(aprendiz)).thenReturn(Optional.empty());

        servicio.completar(soporte, aprendiz);

        ArgumentCaptor<EstadoOnboarding> guardado = ArgumentCaptor.forClass(EstadoOnboarding.class);
        verify(saveEstado).guardar(guardado.capture());
        assertThat(guardado.getValue().completado()).isTrue();
        assertThat(guardado.getValue().terminosAceptadosEn()).isNotNull();
        assertThat(guardado.getValue().pactoFirmadoEn()).isNotNull();
        assertThat(guardado.getValue().rocasSyncAceptadoEn()).isNotNull();
    }

    @Test
    @DisplayName("si ya está completo no vuelve a guardar")
    void idempotente() {
        esCuentaDePrueba(aprendiz, true);
        EstadoOnboarding completo = EstadoOnboarding.iniciar(aprendiz, CLOCK);
        completo.aceptarHito(com.renaser.os.onboarding.domain.model.estado.HitoOnboarding.PACTO_FIRMADO, CLOCK);
        completo.marcarCompletado(CLOCK);
        when(loadEstado.deUsuario(aprendiz)).thenReturn(Optional.of(completo));

        servicio.completar(soporte, aprendiz);

        verify(saveEstado, never()).guardar(any());
    }

    @Test
    @DisplayName("nunca el onboarding de una persona real (400)")
    void nuncaUnaPersonaReal() {
        esCuentaDePrueba(aprendiz, false);

        assertThatThrownBy(() -> servicio.completar(soporte, aprendiz)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(saveEstado);
    }

    @Test
    @DisplayName("solo lo pide Soporte TI activo (403)")
    void soloSoporteTi() {
        UserId admin = UserId.of(UUID.randomUUID());
        when(usuarios.findById(admin)).thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN,
                UserStatus.ACTIVE)));

        assertThatThrownBy(() -> servicio.completar(admin, aprendiz)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(saveEstado);
    }
}
