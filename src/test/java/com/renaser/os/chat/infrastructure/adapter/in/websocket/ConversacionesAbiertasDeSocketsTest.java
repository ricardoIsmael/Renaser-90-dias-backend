package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.application.ports.in.presencia.RegistrarConversacionAbiertaUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-221: la suscripción al topic de una conversación es «la tiene abierta»; la primera abre, la
 * última cierra (dos teléfonos con el mismo chat), y un socket que se cae cierra lo suyo.
 */
class ConversacionesAbiertasDeSocketsTest {

    private final List<String> registro = new ArrayList<>();
    private final ConversacionesAbiertasDeSockets sockets = new ConversacionesAbiertasDeSockets(
            new RegistrarConversacionAbiertaUseCase() {
                @Override
                public void laAbrio(UserId usuarioId, ConversacionId conversacionId) {
                    registro.add("abrio " + conversacionId.value());
                }

                @Override
                public void laCerro(UserId usuarioId, ConversacionId conversacionId) {
                    registro.add("cerro " + conversacionId.value());
                }

                @Override
                public void sigueAbierta(UserId usuarioId, ConversacionId conversacionId) {
                    registro.add("sigue " + conversacionId.value());
                }
            });

    private final UUID ana = UUID.randomUUID();
    private final UUID chat = UUID.randomUUID();

    @Test
    @DisplayName("suscribirse abre, desuscribirse cierra")
    void abreYCierra() {
        sockets.alSuscribirse(suscripcion("s1", "sub-0", "/topic/conversaciones/" + chat));
        sockets.alDesuscribirse(desuscripcion("s1", "sub-0"));

        assertThat(registro).containsExactly("abrio " + chat, "cerro " + chat);
    }

    @Test
    @DisplayName("el mismo chat en dos teléfonos: cerrar uno no lo cierra; el socket que se cae cierra lo suyo")
    void dosTelefonos() {
        sockets.alSuscribirse(suscripcion("s1", "sub-0", "/topic/conversaciones/" + chat));
        sockets.alSuscribirse(suscripcion("s2", "sub-0", "/topic/conversaciones/" + chat));
        sockets.alDesuscribirse(desuscripcion("s1", "sub-0"));
        assertThat(registro).containsExactly("abrio " + chat);

        sockets.renovar();
        sockets.alDesconectarse(new SessionDisconnectEvent(this, mensaje(StompCommand.DISCONNECT, "s2", null, null),
                "s2", CloseStatus.NORMAL));
        assertThat(registro).containsExactly("abrio " + chat, "sigue " + chat, "cerro " + chat);
    }

    @Test
    @DisplayName("un destino que no es de una conversación no cuenta")
    void otroDestino() {
        sockets.alSuscribirse(suscripcion("s1", "sub-0", "/topic/otra-cosa"));
        assertThat(registro).isEmpty();
    }

    private SessionSubscribeEvent suscripcion(String socket, String sub, String destino) {
        return new SessionSubscribeEvent(this, mensaje(StompCommand.SUBSCRIBE, socket, sub, destino));
    }

    private SessionUnsubscribeEvent desuscripcion(String socket, String sub) {
        return new SessionUnsubscribeEvent(this, mensaje(StompCommand.UNSUBSCRIBE, socket, sub, null));
    }

    private Message<byte[]> mensaje(StompCommand comando, String socket, String sub, String destino) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(comando);
        accessor.setSessionId(socket);
        if (sub != null) accessor.setSubscriptionId(sub);
        if (destino != null) accessor.setDestination(destino);
        Map<String, Object> atributos = new HashMap<>();
        atributos.put(ActorHandshakeInterceptor.ATRIBUTO_ACTOR_ID, ana);
        accessor.setSessionAttributes(atributos);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
