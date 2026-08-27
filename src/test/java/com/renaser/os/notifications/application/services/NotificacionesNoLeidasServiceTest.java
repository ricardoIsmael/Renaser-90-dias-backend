package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.out.notificacion.LoadNotificacionPort;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionesNoLeidasServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadNotificacionPort loadNotificacionPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private NotificacionesNoLeidasService service;

    @BeforeEach
    void setUp() {
        service = new NotificacionesNoLeidasService(loadNotificacionPort,
                new ActorNotificacionesGuard(userSummaryFinder), CLOCK);
        lenient().when(userSummaryFinder.findById(any())).thenAnswer(inv -> Optional.of(
                new UserSummary(inv.getArgument(0), "Test", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    private static UserId usuario() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void contarNoLeidasUsaLaVentanaDeRetencionYDelegaEnElPuerto() {
        UserId actor = usuario();
        Instant esperado = CLOCK.now().minus(Notificacion.RETENCION_DIAS, ChronoUnit.DAYS);
        when(loadNotificacionPort.contarNoLeidas(actor, esperado)).thenReturn(7L);

        long resultado = service.contarNoLeidas(actor);

        assertThat(resultado).isEqualTo(7L);
        org.mockito.Mockito.verify(loadNotificacionPort).contarNoLeidas(eq(actor), eq(esperado));
    }

    @Test
    @DisplayName("E-38: una cuenta SUSPENDIDA no puede consultar su conteo de no leidas")
    void actorSuspendidoNoConsultaElConteo() {
        UserId actor = usuario();
        when(userSummaryFinder.findById(actor)).thenReturn(Optional.of(
                new UserSummary(actor, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.contarNoLeidas(actor)).isInstanceOf(NotAuthorizedException.class);
    }
}
