package com.renaser.os.chat.application.services;

import com.renaser.os.chat.api.MensajeDirectoPort.MensajeDirecto;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase.CrearConversacionDirectaCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El puerto que deja a otro módulo escribirle a una persona en su chat directo (spec 056, FR-019, FR-025, FR-029): abre o reutiliza la conversación de
 * dos y guarda un mensaje de texto. El push del mensaje lo dispara el guardado, no este puerto.
 */
@ExtendWith(MockitoExtension.class)
class MensajeDirectoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-18T15:00:00Z");

    @Mock private CrearConversacionDirectaUseCase conversaciones;
    @Mock private EnviarMensajeUseCase mensajes;

    private MensajeDirectoService servicio;

    private final UserId lider = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final Conversacion directa = Conversacion.crearDirecta(ConversacionId.of(UUID.randomUUID()), "a_b", AHORA);

    @BeforeEach
    void preparar() {
        servicio = new MensajeDirectoService(conversaciones, mensajes);
    }

    private MensajeEnriquecido guardado(String texto) {
        Mensaje mensaje = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), directa.id(), lider, TipoMensaje.TEXTO, texto, null, null, null, null, null,
                null, AHORA);
        return new MensajeEnriquecido(mensaje, "Líder", null, null, null);
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019 FR-025 FR-029: abre o reutiliza el chat de dos y guarda el texto como mensaje de la persona que escribe")
    void abreElChatDeDosYGuardaElTexto() {
        when(conversaciones.obtenerOCrear(new CrearConversacionDirectaCommand(lider, mentor))).thenReturn(directa);
        MensajeEnriquecido enviado = guardado("Mira la ficha de Ana");
        when(mensajes.enviar(any())).thenReturn(enviado);

        MensajeDirecto resultado = servicio.enviar(lider, mentor, "Mira la ficha de Ana");

        ArgumentCaptor<EnviarMensajeCommand> comando = ArgumentCaptor.forClass(EnviarMensajeCommand.class);
        verify(mensajes).enviar(comando.capture());
        assertThat(comando.getValue().actorId()).isEqualTo(lider);
        assertThat(comando.getValue().conversacionId()).isEqualTo(directa.id());
        assertThat(comando.getValue().tipo()).isEqualTo(TipoMensaje.TEXTO);
        assertThat(comando.getValue().texto()).isEqualTo("Mira la ficha de Ana");
        assertThat(comando.getValue().mediaRuta()).as("solo texto: ninguna media").isNull();
        assertThat(comando.getValue().origenMedia()).isEqualTo(OrigenMedia.CLIENTE);
        assertThat(resultado.conversacionId()).isEqualTo(directa.id().value());
        assertThat(resultado.mensajeId()).isEqualTo(enviado.mensaje().id().value());
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: si el chat de dos no se puede abrir (directos apagados, otra formación), el motivo sube y no se guarda ningún mensaje")
    void sinChatDeDosNoSeGuardaNada() {
        when(conversaciones.obtenerOCrear(any())).thenThrow(new NotAuthorizedException("Los chats directos no están disponibles en esta formación"));

        assertThatThrownBy(() -> servicio.enviar(lider, mentor, "Hola")).isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("chats directos");

        verify(mensajes, never()).enviar(any());
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: un texto en blanco no abre ni un chat")
    void unTextoEnBlancoNoAbreNada() {
        assertThatThrownBy(() -> servicio.enviar(lider, mentor, "   ")).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(conversaciones, mensajes);
    }
}
