package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.MensajePush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-221: el push de un mensaje de chat sale por el canal de mensajes (con su sonido propio) y con la
 * etiqueta de su conversación, para que el aviso nuevo reemplace al anterior del mismo chat.
 */
class AvisoDeChatEnElPushTest {

    private static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");
    private static final String CONVERSACION = "7d1f7f64-8d4b-4a34-a0b4-2a3a0e1c9f10";
    private static final MensajePush DE_CHAT = new MensajePush(TipoNotificacion.MENSAJE_CHAT,
            "Luisa y sus aprendices (3 mensajes nuevos)", "Ana Pérez: hola", "/chat/" + CONVERSACION);

    private static TokenPush token(PlataformaPush plataforma) {
        return TokenPush.rehydrate(TokenPushId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "ExponentPushToken[abc]", plataforma, AHORA, AHORA);
    }

    private static ExpoPushTransporte expo(boolean canalDeMensajes) {
        return new ExpoPushTransporte("", true, false, false, canalDeMensajes);
    }

    @Test
    @DisplayName("Android: canal «mensajes-chat» y la etiqueta del chat en tag y threadId")
    void android() {
        assertThat(expo(true).cuerpoJson(token(PlataformaPush.ANDROID), DE_CHAT))
                .contains("\"channelId\":\"mensajes-chat\"")
                .contains("\"tag\":\"chat-" + CONVERSACION + "\"")
                .contains("\"threadId\":\"chat-" + CONVERSACION + "\"")
                .contains("\"data\":{\"route\":\"/chat/" + CONVERSACION + "\"}");
    }

    @Test
    @DisplayName("iOS: sin channelId (no existe), pero agrupado por la misma etiqueta")
    void ios() {
        assertThat(expo(true).cuerpoJson(token(PlataformaPush.IOS), DE_CHAT))
                .doesNotContain("channelId")
                .contains("\"threadId\":\"chat-" + CONVERSACION + "\"");
    }

    @Test
    @DisplayName("con el canal de mensajes apagado sale por el canal por defecto, sin perder la etiqueta")
    void canalApagado() {
        assertThat(expo(false).cuerpoJson(token(PlataformaPush.ANDROID), DE_CHAT))
                .doesNotContain("channelId")
                .contains("\"tag\":\"chat-" + CONVERSACION + "\"");
    }

    @Test
    @DisplayName("los demás avisos no llevan etiqueta: cada uno sigue siendo uno aparte")
    void otrosSinEtiqueta() {
        MensajePush otro = new MensajePush(TipoNotificacion.RECORDATORIO_EVENTO, "t", "c", "/chat/" + CONVERSACION);
        assertThat(expo(true).cuerpoJson(token(PlataformaPush.ANDROID), otro)).doesNotContain("\"tag\"");
    }

    @Test
    @DisplayName("web: el navegador recibe la etiqueta para mostrar uno por chat; los demás, el cuerpo de siempre")
    void web() {
        WebPushAdapter web = new WebPushAdapter("", "", "");
        assertThat(web.cuerpoDe(DE_CHAT)).containsEntry("tag", "chat-" + CONVERSACION)
                .containsEntry("title", "Luisa y sus aprendices (3 mensajes nuevos)");
        assertThat(web.cuerpoDe(new MensajePush(TipoNotificacion.HITO_PROGRAMA, "t", "c", "/caja")))
                .doesNotContainKey("tag");
    }
}
