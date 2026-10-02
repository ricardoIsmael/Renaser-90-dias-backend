package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.emergencia.AvisoDeEmergencia;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EmergenciaPedidaEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-244: el pedido de emergencia llega al chat de soporte del aprendiz. */
@ExtendWith(MockitoExtension.class)
class EmergenciaEnSoporteServiceTest {

    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.randomUUID());

    @Mock
    private LoadConversacionPort conversaciones;
    @Mock
    private EnviarMensajeDelProgramaUseCase delPrograma;

    private final EmergenciaPedidaEvent pedido = new EmergenciaPedidaEvent(UUID.randomUUID(), ANA, "Accidente", 12, 20);

    @Test
    @DisplayName("un mensaje del programa, con id del pedido y sin push del chat (el aviso a soporte va aparte)")
    void dejaElMensajeEnSuSoporte() {
        when(conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(ANA))).thenReturn(Optional.of(
                Conversacion.crearSoporte(SOPORTE, ANA, "Ana – Formación Renaser", Instant.now())));

        new EmergenciaEnSoporteService(conversaciones, delPrograma).avisar(pedido);

        ArgumentCaptor<EntregaDelPrograma> entrega = ArgumentCaptor.forClass(EntregaDelPrograma.class);
        verify(delPrograma).enviarUnaVez(entrega.capture());
        assertThat(entrega.getValue().conversacionId()).isEqualTo(SOPORTE);
        assertThat(entrega.getValue().sobreQuien()).isEqualTo(ANA);
        var esperado = new AvisoDeEmergencia(pedido.solicitudId(), "Accidente", 12, 20);
        assertThat(entrega.getValue().piezas()).singleElement().satisfies(pieza -> {
            assertThat(pieza.id()).isEqualTo(esperado.idDelMensaje());
            assertThat(pieza.contenido().texto()).isEqualTo(esperado.texto());
            assertThat(pieza.aviso()).isEqualTo(AvisoDeLaPieza.SIN_AVISO);
        });
    }

    @Test
    @DisplayName("sin chat de soporte no escribe nada (y no falla: el outbox no reintentaría para siempre)")
    void sinSoporte() {
        when(conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(ANA))).thenReturn(Optional.empty());

        new EmergenciaEnSoporteService(conversaciones, delPrograma).avisar(pedido);

        verifyNoInteractions(delPrograma);
    }
}
