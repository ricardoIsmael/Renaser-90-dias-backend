package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.CompletarChatsDeAprendicesUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** D-224: solo una cuenta que RECUPERA el acceso dispara la completitud de sus chats. */
class EstadoDeCuentaCambiadoChatsListenerTest {

    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final Instant AHORA = Instant.parse("2026-09-29T03:00:00Z");

    private final CompletarChatsDeAprendicesUseCase completar = mock(CompletarChatsDeAprendicesUseCase.class);
    private final EstadoDeCuentaCambiadoChatsListener listener = new EstadoDeCuentaCambiadoChatsListener(completar);

    @Test
    @DisplayName("al reactivarse una cuenta suspendida, completa sus chats")
    void reactivar() {
        listener.on(new EstadoDeCuentaCambiadoEvent(ANA, UserStatus.SUSPENDED, UserStatus.ACTIVE, AHORA));

        verify(completar).completarDe(ANA);
    }

    @Test
    @DisplayName("suspender o pasar a INACTIVE no crea nada")
    void suspenderNo() {
        listener.on(new EstadoDeCuentaCambiadoEvent(ANA, UserStatus.ACTIVE, UserStatus.SUSPENDED, AHORA));
        listener.on(new EstadoDeCuentaCambiadoEvent(ANA, UserStatus.ACTIVE, UserStatus.INACTIVE, AHORA));

        verify(completar, never()).completarDe(any());
    }
}
