package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.tokenpush.RevocarTokensPushUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit puro (sin Spring): el listener decide por el estado NUEVO, no por el nombre de la
 * transicion. La prueba de que el outbox de Modulith entrega los eventos de verdad vive en
 * {@link NotificationsEventOutboxTest}.
 */
@ExtendWith(MockitoExtension.class)
class EstadoDeCuentaCambiadoTokensPushListenerTest {

    @Mock
    private RevocarTokensPushUseCase revocarTokensPushUseCase;

    private static UserId usuario() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("suspender revoca los tokens push, como cerrarTodas revoca las sesiones")
    void suspenderRevocaLosTokensPush() {
        var listener = new EstadoDeCuentaCambiadoTokensPushListener(revocarTokensPushUseCase);
        UserId suspendido = usuario();

        listener.on(new EstadoDeCuentaCambiadoEvent(suspendido, UserStatus.ACTIVE, UserStatus.SUSPENDED,
                Instant.parse("2026-09-21T10:00:00Z")));

        verify(revocarTokensPushUseCase).revocarDe(suspendido);
    }

    @Test
    @DisplayName("reactivar NO revoca nada: el telefono vuelve a registrarse solo en cuanto haya sesion")
    void reactivarNoRevocaNada() {
        var listener = new EstadoDeCuentaCambiadoTokensPushListener(revocarTokensPushUseCase);

        listener.on(new EstadoDeCuentaCambiadoEvent(usuario(), UserStatus.SUSPENDED, UserStatus.ACTIVE,
                Instant.parse("2026-09-21T10:00:00Z")));

        verify(revocarTokensPushUseCase, never()).revocarDe(any());
    }
}
