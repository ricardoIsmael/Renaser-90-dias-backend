package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.leadership.api.ObservacionDelLiderEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
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

/** «Tu líder te dejó una observación» (spec 056, FR-025): a quién le llega el aviso, qué dice, con qué clave y a dónde lleva. */
@ExtendWith(MockitoExtension.class)
class ObservacionDelLiderNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitir;

    private final UUID clave = UUID.randomUUID();
    private final UUID mentor = UUID.randomUUID();
    private final UUID autor = UUID.randomUUID();
    private final UUID conversacion = UUID.randomUUID();

    private EmitirNotificacionCommand emitidoPara(String tipo, UUID conversacionId) {
        new ObservacionDelLiderNotificationListener(emitir).on(new ObservacionDelLiderEvent(clave, UUID.randomUUID(), mentor, autor, tipo, conversacionId,
                Instant.parse("2026-11-18T15:00:00Z")));
        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir).emitir(captor.capture());
        return captor.getValue();
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025: le llega al mentor, con la clave de deduplicación del evento y la ruta del chat donde está el texto")
    void leLlegaAlMentor() {
        EmitirNotificacionCommand comando = emitidoPara("SUGERENCIA", conversacion);

        assertThat(comando.usuarioId()).isEqualTo(UserId.of(mentor));
        assertThat(comando.tipo()).isEqualTo(TipoNotificacion.REVISION_DEL_LIDER);
        assertThat(comando.rutaApp()).isEqualTo("/chat/" + conversacion);
        assertThat(comando.origenEventoId()).as("la redelivery del outbox no duplica el aviso").isEqualTo(clave);
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025: el título dice qué le dejó el líder y el cuerpo no repite el texto: va por push y puede verse en la pantalla bloqueada")
    void elTituloDiceQueLeDejoSinRepetirElTexto() {
        EmitirNotificacionCommand comando = emitidoPara("ALERTA", conversacion);

        assertThat(comando.titulo()).isEqualTo("Tu líder te dejó una alerta");
        assertThat(comando.cuerpo()).isEqualTo("El texto está en tu chat con tu líder.");
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025: un reconocimiento se avisa como reconocimiento")
    void unReconocimientoSeAvisaComoTal() {
        assertThat(emitidoPara("RECONOCIMIENTO", conversacion).titulo()).isEqualTo("Tu líder te dejó un reconocimiento");
    }

    // spec 056 FR-025
    @Test
    @DisplayName("spec 056 FR-025: de una app vieja no se conoce el chat: el aviso sale igual, sin ruta")
    void sinChatConocidoElAvisoSaleSinRuta() {
        assertThat(emitidoPara("SUGERENCIA", null).rutaApp()).isNull();
    }
}
