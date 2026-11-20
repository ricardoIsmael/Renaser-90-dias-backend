package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.notifications.application.ports.out.push.MensajePush;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los botones del push de Expo (spec 057, FR-035): {@code categoryId} y los datos extra solo salen cuando el aviso los trae; el cuerpo de
 * todos los demás pushes es EXACTAMENTE el de antes.
 */
class ExpoPushTransporteBotonesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant AHORA = Instant.parse("2026-11-21T00:05:00Z");

    private static TokenPush token() {
        return TokenPush.rehydrate(TokenPushId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), "ExponentPushToken[abc]",
                PlataformaPush.ANDROID, AHORA, AHORA);
    }

    private static String cuerpo(MensajePush mensaje) {
        return new ExpoPushTransporte("", true, false, false, false).cuerpoJson(token(), mensaje);
    }

    @Test
    @DisplayName("spec 057 FR-035: el aviso de rojo manda categoryId rojo-del-dia y, en data, route, grupoId y aprendizId")
    void elAvisoDeRojoLlevaBotones() throws Exception { // spec 057 FR-035
        MensajePush rojo = new MensajePush(TipoNotificacion.ROJO_DEL_DIA, "Ana está en rojo hoy", "Lleva 33 %.", "/mentor/groups/g/learners/a",
                null, null, "rojo-del-dia", Map.of("grupoId", "g", "aprendizId", "a"));

        JsonNode json = JSON.readTree(cuerpo(rojo));

        assertThat(json.get("categoryId").asText()).isEqualTo("rojo-del-dia");
        assertThat(json.get("data").get("route").asText()).isEqualTo("/mentor/groups/g/learners/a");
        assertThat(json.get("data").get("grupoId").asText()).isEqualTo("g");
        assertThat(json.get("data").get("aprendizId").asText()).isEqualTo("a");
    }

    @Test
    @DisplayName("spec 057 FR-035: un push sin categoría ni extras no lleva categoryId y su data es solo route, como siempre")
    void losDemasPushNoCambian() throws Exception { // spec 057 FR-035
        JsonNode json = JSON.readTree(cuerpo(new MensajePush(TipoNotificacion.ACOMPANAMIENTO_ALUMNO, "T", "C", "/mentor/groups/g/learners/a")));

        assertThat(json.has("categoryId")).isFalse();
        assertThat(json.get("data").size()).isEqualTo(1);
        assertThat(json.get("data").get("route").asText()).isEqualTo("/mentor/groups/g/learners/a");
    }
}
