package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.shared.web.security.SesionDeSoloLectura;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * S-4 (auditoria 2026-09-01), cerrado 2026-09-06: un cliente no publica directo en {@code /topic}.
 * El broker simple reparte a los suscriptores todo lo que reciba ahi, venga de donde venga; sin
 * esta guarda un socket autenticado podia escribir en la conversacion de otros saltandose el caso
 * de uso.
 */
@ExtendWith(MockitoExtension.class)
class SubscripcionAutorizadaInterceptorEnvioTest {

    @Mock
    private AutorizarAccesoAConversacionUseCase autorizarAcceso;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private MessageChannel canal;
    @Mock
    private SesionViva sesionViva;
    @Mock
    private AutorizacionViva autorizacionViva;

    private SubscripcionAutorizadaInterceptor interceptor() {
        return new SubscripcionAutorizadaInterceptor(autorizarAcceso, userSummaryFinder, sesionViva, autorizacionViva, (conversacion, actor) -> { });
    }

    private static Message<byte[]> envioA(String destino) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destino);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("un SEND directo a /topic/... se rechaza antes de llegar al broker")
    void envioDirectoAlTopicSeRechaza() {
        assertThatThrownBy(() -> interceptor().preSend(envioA("/topic/conversaciones/abc"), canal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("/app");
        verifyNoInteractions(autorizarAcceso, userSummaryFinder);
    }

    @Test
    @DisplayName("un SEND a /app/... (la via legitima) pasa sin tocar autorizacion de suscripcion")
    void envioALaAplicacionPasa() {
        Message<byte[]> mensaje = envioA("/app/conversaciones/abc/mensajes");

        assertThat(interceptor().preSend(mensaje, canal)).isSameAs(mensaje);
        verifyNoInteractions(autorizarAcceso, userSummaryFinder);
    }

    @Test
    @DisplayName("«ver como» (D-270): una sesión de solo lectura no manda nada, ni a /app (MODO_SOLO_LECTURA)")
    void soloLecturaNoManda() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/conversaciones/abc/mensajes");
        accessor.setSessionAttributes(new java.util.HashMap<>(java.util.Map.of(SesionDeSoloLectura.ABIERTA_POR,
                java.util.UUID.randomUUID().toString())));
        Message<byte[]> mensaje = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor().preSend(mensaje, canal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("MODO_SOLO_LECTURA");
        verifyNoInteractions(autorizarAcceso, userSummaryFinder);
    }
}
