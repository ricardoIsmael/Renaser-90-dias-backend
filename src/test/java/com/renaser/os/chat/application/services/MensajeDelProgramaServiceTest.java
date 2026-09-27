package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El programa escribe en un chat (D-199/D-204): SISTEMA, a nombre de la persona a quien se refiere, sin
 * pedir actor activo ni participante, y empujado en vivo. Sin transacción activa se publica al toque
 * (el camino con transacción es el mismo mecanismo de {@code MensajeService}).
 */
@ExtendWith(MockitoExtension.class)
class MensajeDelProgramaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T04:30:00Z");
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UUID ID = UUID.randomUUID();

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private SaveMensajePort saveMensajePort;
    @Mock
    private PublicarMensajeFanoutPort fanout;

    private MensajeDelProgramaService servicio() {
        return new MensajeDelProgramaService(loadConversacionPort, saveMensajePort, fanout, FixedClock.at(AHORA),
                () -> ID);
    }

    @Test
    @DisplayName("guarda un SISTEMA a nombre de la persona a quien se refiere y lo empuja en vivo")
    void guardaYEmpuja() {
        when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.of(Conversacion.crearGlobal(SOPORTE, AHORA)));
        when(saveMensajePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Mensaje guardado = servicio().enviarDelPrograma(SOPORTE, ANA, ContenidoDelPrograma.texto("Te damos la bienvenida"));

        assertThat(guardado.tipo()).isEqualTo(TipoMensaje.SISTEMA);
        assertThat(guardado.emisorId()).isEqualTo(ANA);
        assertThat(guardado.id().value()).isEqualTo(ID);
        assertThat(guardado.creadoEn()).isEqualTo(AHORA);
        assertThat(guardado.texto()).isEqualTo("Te damos la bienvenida");
        verify(fanout).publicar(guardado);
    }

    @Test
    @DisplayName("sin la conversación no guarda nada (la bienvenida lanza y el outbox reintenta)")
    void sinConversacionNoGuarda() {
        when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().enviarDelPrograma(SOPORTE, ANA, ContenidoDelPrograma.texto("hola")))
                .isInstanceOf(NoSuchElementException.class);
        verify(saveMensajePort, never()).save(any());
        verify(fanout, never()).publicar(any());
    }
}
