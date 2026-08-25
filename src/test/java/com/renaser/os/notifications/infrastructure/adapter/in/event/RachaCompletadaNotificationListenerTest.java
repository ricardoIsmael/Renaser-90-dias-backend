package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.RachaCompletadaEvent;
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

@ExtendWith(MockitoExtension.class)
class RachaCompletadaNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;

    @Test
    void traduceElEventoAUnaNotificacionDeLogro() {
        var listener = new RachaCompletadaNotificationListener(emitirNotificacionUseCase);
        UserId participante = UserId.of(UUID.randomUUID());
        var event = new RachaCompletadaEvent(UUID.randomUUID(), participante, Instant.now());

        listener.on(event);

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(captor.capture());
        assertThat(captor.getValue().usuarioId()).isEqualTo(participante);
        assertThat(captor.getValue().tipo()).isEqualTo(TipoNotificacion.LOGRO_DESBLOQUEADO);
    }
}
