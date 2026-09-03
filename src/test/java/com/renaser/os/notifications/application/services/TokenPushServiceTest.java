package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.tokenpush.RegistrarTokenPushUseCase.RegistrarTokenPushCommand;
import com.renaser.os.notifications.application.ports.out.tokenpush.UpsertTokenPushPort;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenPushServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, registrar() ya no sortea el TokenPushId. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private UpsertTokenPushPort upsertTokenPushPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private IdGenerator idGenerator;

    private TokenPushService service;

    @BeforeEach
    void setUp() {
        service = new TokenPushService(upsertTokenPushPort, new ActorNotificacionesGuard(userSummaryFinder),
                CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(upsertTokenPushPort.upsertPorToken(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userSummaryFinder.findById(any())).thenAnswer(inv -> Optional.of(
                new UserSummary(inv.getArgument(0), "Test", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    @Test
    void registrarConstruyeElAgregadoYLoDelegaAlPuertoDeUpsert() {
        UserId usuario = UserId.of(UUID.randomUUID());

        TokenPush resultado = service.registrar(
                new RegistrarTokenPushCommand(usuario, "expo-tok-1", PlataformaPush.IOS));

        ArgumentCaptor<TokenPush> captor = ArgumentCaptor.forClass(TokenPush.class);
        verify(upsertTokenPushPort).upsertPorToken(captor.capture());
        assertThat(captor.getValue().usuarioId()).isEqualTo(usuario);
        assertThat(captor.getValue().token()).isEqualTo("expo-tok-1");
        assertThat(resultado.token()).isEqualTo("expo-tok-1");
    }

    @Test
    @DisplayName("E-38: una cuenta SUSPENDIDA no puede registrar un token push (CLAUDE.MD §0.3)")
    void actorSuspendidoNoPuedeRegistrarToken() {
        UserId usuario = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(usuario)).thenReturn(Optional.of(
                new UserSummary(usuario, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.registrar(
                new RegistrarTokenPushCommand(usuario, "expo-tok-1", PlataformaPush.IOS)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(upsertTokenPushPort, never()).upsertPorToken(any());
    }

    @Test
    @DisplayName("E-38: un X-Actor-Id inexistente no registra nada")
    void actorInexistenteNoPuedeRegistrarToken() {
        UserId usuario = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(usuario)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(
                new RegistrarTokenPushCommand(usuario, "expo-tok-1", PlataformaPush.IOS)))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verify(upsertTokenPushPort, never()).upsertPorToken(any());
    }
}
