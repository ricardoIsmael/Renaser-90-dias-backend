package com.renaser.os.rag.infrastructure.adapter.in.websocket;

import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.ConversacionEnVivo;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.MotivoDeCierre;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.SalidaDeVozEnVivo;
import com.renaser.os.rag.domain.model.conversacion.EventoDeVozEnVivo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El socket solo traduce (D-162): frames ⇄ caso de uso, con el contrato de §5.ter al pie de la letra. */
class VozEnVivoWebSocketHandlerTest {

    private final UserId actor = UserId.of(UUID.randomUUID());
    private final ConversarEnVivoUseCase useCase = mock(ConversarEnVivoUseCase.class);
    private final ConversacionEnVivo conversacion = mock(ConversacionEnVivo.class);
    private final WebSocketSession session = mock(WebSocketSession.class);
    private final Map<String, Object> atributos = new HashMap<>();
    private final VozEnVivoWebSocketHandler handler = new VozEnVivoWebSocketHandler(useCase);

    private SalidaDeVozEnVivo abrir() throws Exception {
        atributos.put(VozEnVivoHandshakeInterceptor.ATRIBUTO_ACTOR, actor);
        when(session.getAttributes()).thenReturn(atributos);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s-1");
        when(useCase.iniciar(eq(actor), any())).thenReturn(conversacion);
        handler.afterConnectionEstablished(session);
        ArgumentCaptor<SalidaDeVozEnVivo> salida = ArgumentCaptor.forClass(SalidaDeVozEnVivo.class);
        verify(useCase).iniciar(eq(actor), salida.capture());
        return salida.getValue();
    }

    @Test
    @DisplayName("al conectar inicia la conversacion con el actor del handshake")
    void iniciaConElActor() throws Exception {
        abrir();

        assertThat(atributos).containsEntry(VozEnVivoWebSocketHandler.ATRIBUTO_CONVERSACION, conversacion);
    }

    @Test
    @DisplayName("si la app cerro mientras se abria la sesion con Gemini, la conversacion se termina enseguida")
    void cerroMientrasAbria() throws Exception {
        atributos.put(VozEnVivoHandshakeInterceptor.ATRIBUTO_ACTOR, actor);
        when(session.getAttributes()).thenReturn(atributos);
        when(session.isOpen()).thenReturn(false);
        when(useCase.iniciar(eq(actor), any())).thenReturn(conversacion);

        handler.afterConnectionEstablished(session);

        verify(conversacion).terminar();
    }

    @Test
    @DisplayName("un frame binario es audio de la persona")
    void binarioEsAudio() throws Exception {
        abrir();

        handler.handleMessage(session, new BinaryMessage(new byte[]{1, 2, 3, 4}));

        ArgumentCaptor<byte[]> audio = ArgumentCaptor.forClass(byte[].class);
        verify(conversacion).recibirAudio(audio.capture());
        assertThat(audio.getValue()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("un frame de audio partido en pedazos llega entero, una sola vez (E-234)")
    void frameParticionado() throws Exception {
        abrir();

        handler.handleMessage(session, new BinaryMessage(new byte[]{1, 2, 3}, false));
        handler.handleMessage(session, new BinaryMessage(new byte[]{4, 5, 6}, true));

        ArgumentCaptor<byte[]> audio = ArgumentCaptor.forClass(byte[].class);
        verify(conversacion, times(1)).recibirAudio(audio.capture());
        assertThat(audio.getValue()).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(handler.supportsPartialMessages()).isTrue();
    }

    @Test
    @DisplayName("un frame que pasa el tope se descarta entero, sin llenar la memoria")
    void framePasadoDelTope() throws Exception {
        abrir();

        handler.handleMessage(session, new BinaryMessage(new byte[FramesPartidos.TOPE], false));
        handler.handleMessage(session, new BinaryMessage(new byte[10], true));
        handler.handleMessage(session, new BinaryMessage(new byte[]{9}, true));

        ArgumentCaptor<byte[]> audio = ArgumentCaptor.forClass(byte[].class);
        verify(conversacion, times(1)).recibirAudio(audio.capture());
        assertThat(audio.getValue()).containsExactly(9);
    }

    @Test
    @DisplayName("{\"tipo\":\"fin\"} termina; cualquier otro texto se ignora")
    void finTermina() throws Exception {
        abrir();

        handler.handleMessage(session, new TextMessage("{\"tipo\":\"otra\"}"));
        handler.handleMessage(session, new TextMessage("no es json"));
        verify(conversacion, never()).terminar();

        handler.handleMessage(session, new TextMessage("{\"tipo\":\"fin\"}"));
        verify(conversacion).terminar();
    }

    @Test
    @DisplayName("si se corta la conexion, la conversacion termina")
    void cierreTermina() throws Exception {
        abrir();

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(conversacion, times(1)).terminar();
    }

    @Test
    @DisplayName("la salida manda audio en binario y eventos en texto JSON")
    void salidaAudioYEventos() throws Exception {
        SalidaDeVozEnVivo salida = abrir();

        salida.audio(new byte[]{7, 8});
        salida.evento(new EventoDeVozEnVivo.Listo(600));

        ArgumentCaptor<WebSocketMessage<?>> enviados = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(2)).sendMessage(enviados.capture());
        assertThat(enviados.getAllValues().get(0)).isInstanceOf(BinaryMessage.class);
        assertThat(enviados.getAllValues().get(1).getPayload()).isEqualTo("{\"tipo\":\"listo\",\"segundosRestantes\":600}");
    }

    @Test
    @DisplayName("cada motivo de cierre tiene su codigo: cuota 1000, no disponible 1013, error 1011")
    void codigosDeCierre() throws Exception {
        SalidaDeVozEnVivo salida = abrir();

        salida.cerrar(MotivoDeCierre.NO_DISPONIBLE);

        verify(session).close(new CloseStatus(1013, "no-disponible"));
        assertThat(VozEnVivoWebSocketHandler.estadoDeCierre(MotivoDeCierre.NORMAL)).isEqualTo(CloseStatus.NORMAL);
        assertThat(VozEnVivoWebSocketHandler.estadoDeCierre(MotivoDeCierre.CUOTA_AGOTADA))
                .isEqualTo(new CloseStatus(1000, "cuota-agotada"));
        assertThat(VozEnVivoWebSocketHandler.estadoDeCierre(MotivoDeCierre.ERROR)).isEqualTo(new CloseStatus(1011, "error"));
    }

    @Test
    @DisplayName("cada evento tiene la forma exacta del contrato")
    void formaDeLosEventos() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertThat(EventoDeVozEnVivoJson.aJson(new EventoDeVozEnVivo.Oido("Hola"))).isEqualTo("{\"tipo\":\"oido\",\"texto\":\"Hola\"}");
        assertThat(EventoDeVozEnVivoJson.aJson(new EventoDeVozEnVivo.Dicho("Bien"))).isEqualTo("{\"tipo\":\"dicho\",\"texto\":\"Bien\"}");
        assertThat(EventoDeVozEnVivoJson.aJson(new EventoDeVozEnVivo.Interrumpido())).isEqualTo("{\"tipo\":\"interrumpido\"}");
        assertThat(EventoDeVozEnVivoJson.aJson(new EventoDeVozEnVivo.TurnoCompleto())).isEqualTo("{\"tipo\":\"turnoCompleto\"}");
        assertThat(EventoDeVozEnVivoJson.aJson(new EventoDeVozEnVivo.CuotaAgotada())).isEqualTo("{\"tipo\":\"cuotaAgotada\"}");
        assertThat(EventoDeVozEnVivoJson.aJson(new EventoDeVozEnVivo.Error("No se pudo")))
                .isEqualTo("{\"tipo\":\"error\",\"valor\":\"No se pudo\"}");
        assertThat(EventoDeVozEnVivoJson.aJson(new EventoDeVozEnVivo.Propuesta(id, "Marcar Meditar",
                Instant.parse("2026-09-24T15:10:00Z"))))
                .isEqualTo("{\"tipo\":\"propuesta\",\"id\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"resumen\":\"Marcar Meditar\",\"venceEn\":\"2026-09-24T15:10:00Z\"}");
        // D-171: la tarjeta de la camara, igual que en el SSE del chat.
        assertThat(EventoDeVozEnVivoJson.aJson(new EventoDeVozEnVivo.Evidencia(id, "JUGO VERDE",
                Instant.parse("2026-09-27T05:00:00Z"))))
                .isEqualTo("{\"tipo\":\"evidencia\",\"registroId\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"titulo\":\"JUGO VERDE\",\"venceEn\":\"2026-09-27T05:00:00Z\"}");
    }
}
