package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.UnirseAConversacionGlobalUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

/** Unit puro (sin Spring): confirma que el listener traduce el evento en la union a
 * GLOBAL. La entrega real via el outbox de Modulith es infraestructura de Spring, no se
 * reprueba aca (mismo criterio que {@code HabitoCompletadoNotificationListenerTest}). */
@ExtendWith(MockitoExtension.class)
class UsuarioRegistradoChatListenerTest {

    @Mock
    private UnirseAConversacionGlobalUseCase unirseUseCase;

    @Test
    void agregaAlUsuarioRegistradoALaConversacionGlobal() {
        var listener = new UsuarioRegistradoChatListener(unirseUseCase);
        UserId usuarioId = UserId.of(UUID.randomUUID());

        listener.on(new UsuarioRegistradoEvent(usuarioId, Instant.now()));

        verify(unirseUseCase).unirse(usuarioId);
    }
}
