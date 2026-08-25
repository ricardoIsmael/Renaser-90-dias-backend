package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.tokenpush.RegistrarTokenPushUseCase.RegistrarTokenPushCommand;
import com.renaser.os.notifications.application.ports.out.tokenpush.UpsertTokenPushPort;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenPushServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private UpsertTokenPushPort upsertTokenPushPort;

    private TokenPushService service;

    @BeforeEach
    void setUp() {
        service = new TokenPushService(upsertTokenPushPort, CLOCK);
        when(upsertTokenPushPort.upsertPorToken(any())).thenAnswer(inv -> inv.getArgument(0));
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
}
