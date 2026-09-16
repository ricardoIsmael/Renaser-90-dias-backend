package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.IncorporarUsuarioAlSoporteUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolDeUsuarioCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * Los dos avisos que disparan el chat de soporte (D-136). Unit puro: confirman que el listener
 * traduce el evento al caso de uso y <b>no decide nada por su cuenta</b> — quien mira el rol y
 * resuelve que corresponde es el dominio. La entrega real via el outbox de Modulith es
 * infraestructura de Spring y no se reprueba aca (mismo criterio que
 * {@code UsuarioRegistradoChatListenerTest}).
 */
@ExtendWith(MockitoExtension.class)
class SoporteChatListenersTest {

    private static final UserId USUARIO = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));

    @Mock
    private IncorporarUsuarioAlSoporteUseCase incorporarUseCase;

    @Test
    @DisplayName("el alta de un usuario lo incorpora al circuito de soporte")
    void elAltaDeUnUsuarioLoIncorporaAlCircuitoDeSoporte() {
        new UsuarioRegistradoSoporteListener(incorporarUseCase)
                .on(new UsuarioRegistradoEvent(USUARIO, Instant.parse("2026-09-16T03:00:00Z")));

        verify(incorporarUseCase).incorporar(USUARIO);
    }

    @Test
    @DisplayName("un cambio de rol tambien lo incorpora: el ascenso a ADMIN no puede pasar inadvertido")
    void unCambioDeRolTambienLoIncorpora() {
        new RolDeUsuarioCambiadoSoporteListener(incorporarUseCase)
                .on(new RolDeUsuarioCambiadoEvent(USUARIO, UserRole.MENTOR, UserRole.ADMIN,
                        Instant.parse("2026-09-16T03:00:00Z")));

        verify(incorporarUseCase).incorporar(USUARIO);
    }
}
