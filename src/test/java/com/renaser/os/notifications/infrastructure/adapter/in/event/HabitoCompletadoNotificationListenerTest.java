package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.HabitoCompletadoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Unit puro (sin Spring): confirma que el listener traduce el evento a un
 * {@link EmitirNotificacionCommand} correcto — la prueba de que el MECANISMO de outbox
 * realmente entrega el evento vive en {@link NotificationsEventOutboxIT} (Testcontainers). */
@ExtendWith(MockitoExtension.class)
class HabitoCompletadoNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;

    @Test
    void traduceElEventoAUnaNotificacionDeLogroConLosPuntosOtorgados() {
        var listener = new HabitoCompletadoNotificationListener(emitirNotificacionUseCase);
        UserId participante = UserId.of(UUID.randomUUID());
        UUID registroId = UUID.randomUUID();
        var event = new HabitoCompletadoEvent(registroId, participante, UUID.randomUUID(), 10, Instant.now());

        listener.on(event);

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(captor.capture());
        assertThat(captor.getValue().usuarioId()).isEqualTo(participante);
        assertThat(captor.getValue().tipo()).isEqualTo(TipoNotificacion.LOGRO_DESBLOQUEADO);
        assertThat(captor.getValue().cuerpo()).contains("10");
        // C-7: registroId viaja como origenEventoId -- es la clave que deduplica una redelivery.
        assertThat(captor.getValue().origenEventoId()).isEqualTo(registroId);
    }
}
