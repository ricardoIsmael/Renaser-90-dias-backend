package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.notifications.application.ports.out.push.MensajePush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-217 (2026-09-28): todo push de Expo sale con {@code priority: "high"}. Sin el campo, Expo usa
 * {@code default}, que en Android es un mensaje FCM de prioridad NORMAL: con el teléfono en reposo
 * (Doze) se entrega en lote, tarde, y el respaldo de la alarma local llegaba a destiempo. Es lo que
 * hace que WhatsApp avise con la app cerrada. Contra el código anterior estas pruebas fallan: el cuerpo
 * no llevaba {@code priority}.
 */
class ExpoPushTransportePrioridadTest {

    private static final Instant AHORA = Instant.parse("2026-09-28T07:00:00Z");

    private static String cuerpo(boolean canales, PlataformaPush plataforma, TipoNotificacion tipo) {
        TokenPush token = TokenPush.rehydrate(TokenPushId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "ExponentPushToken[abc]", plataforma, AHORA, AHORA);
        return new ExpoPushTransporte("", true, canales, canales)
                .cuerpoJson(token, new MensajePush(tipo, "Titulo", "Cuerpo", "/ruta"));
    }

    @ParameterizedTest
    @EnumSource(TipoNotificacion.class)
    @DisplayName("Android: todo tipo sale con prioridad alta, con los canales apagados o encendidos")
    void androidPrioridadAlta(TipoNotificacion tipo) throws Exception {
        for (boolean canales : new boolean[] {false, true}) {
            var json = new ObjectMapper().readTree(cuerpo(canales, PlataformaPush.ANDROID, tipo));
            assertThat(json.path("priority").asText()).isEqualTo("high");
        }
    }

    @ParameterizedTest
    @EnumSource(value = PlataformaPush.class, names = {"IOS"})
    @DisplayName("iOS también: Expo lo traduce a apns-priority 10 (entrega inmediata)")
    void iosPrioridadAlta(PlataformaPush plataforma) throws Exception {
        var json = new ObjectMapper().readTree(cuerpo(false, plataforma, TipoNotificacion.RECORDATORIO_HABITO));
        assertThat(json.path("priority").asText()).isEqualTo("high");
    }
}
