package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketMentorRespondidoEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * E-219: cuando el mentor respondia un ticket, al aprendiz no le llegaba nada. Contra el codigo viejo
 * estos tests no compilan: no existia ningun listener de {@link TicketMentorRespondidoEvent}.
 */
@ExtendWith(MockitoExtension.class)
class TicketMentorRespondidoNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;

    @Test
    @DisplayName("le avisa al APRENDIZ, con TICKET_RESPONDIDO y el ticket como clave de deduplicacion")
    void avisaAlAprendiz() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        UUID ticket = UUID.randomUUID();

        new TicketMentorRespondidoNotificationListener(emitirNotificacionUseCase)
                .on(new TicketMentorRespondidoEvent(ticket, aprendiz, Instant.parse("2026-09-23T15:00:00Z")));

        ArgumentCaptor<EmitirNotificacionCommand> comando = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(comando.capture());
        assertThat(comando.getValue().usuarioId()).isEqualTo(aprendiz);
        assertThat(comando.getValue().tipo()).isEqualTo(TipoNotificacion.TICKET_RESPONDIDO);
        assertThat(comando.getValue().origenEventoId()).isEqualTo(ticket);
    }

    @Test
    @DisplayName("el texto es fijo: nunca lleva la respuesta del mentor, que sale por push a la pantalla bloqueada")
    void textoFijoSinContenido() {
        new TicketMentorRespondidoNotificationListener(emitirNotificacionUseCase)
                .on(new TicketMentorRespondidoEvent(UUID.randomUUID(), UserId.of(UUID.randomUUID()), Instant.now()));

        ArgumentCaptor<EmitirNotificacionCommand> comando = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(comando.capture());
        assertThat(comando.getValue().titulo()).isEqualTo(TicketMentorRespondidoNotificationListener.TITULO);
        assertThat(comando.getValue().cuerpo()).isEqualTo(TicketMentorRespondidoNotificationListener.CUERPO);
    }
}
