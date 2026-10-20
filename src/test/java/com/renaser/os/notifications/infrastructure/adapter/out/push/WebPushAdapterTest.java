package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.MensajePush;
import com.renaser.os.notifications.application.ports.out.push.ResultadoEnvioPush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class WebPushAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static TokenPush web() {
        return TokenPush.registrar(TokenPushId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "{\"endpoint\":\"https://push.example.test/sub\",\"keys\":{\"p256dh\":\"key\",\"auth\":\"auth\"}}",
                PlataformaPush.WEB, CLOCK);
    }

    @Test
    @DisplayName("sin VAPID no rompe la emision y lo DICE, en vez de fingir una entrega")
    void sinVapidConfiguradoNoRompeLaEmision() {
        WebPushAdapter adapter = new WebPushAdapter("", "", "");
        TokenPush token = web();

        assertThatCode(() -> adapter.entregar(token, new MensajePush(null, "Aviso", "Cuerpo", null))).doesNotThrowAnyException();

        // El cambio respecto de antes: el resultado deja de ser invisible. Sin credencial no hay
        // canal, y eso no es lo mismo que "entregado".
        assertThat(adapter.entregar(token, new MensajePush(null, "Aviso", "Cuerpo", null)).estado())
                .isEqualTo(ResultadoEnvioPush.Estado.SIN_TRANSPORTE);
    }

    @Test
    @DisplayName("solo atiende WEB: los nativos no son asunto suyo")
    void noTocaElCanalNativo() {
        WebPushAdapter adapter = new WebPushAdapter("", "", "");

        assertThat(adapter.atiende(PlataformaPush.WEB)).isTrue();
        assertThat(adapter.atiende(PlataformaPush.IOS)).isFalse();
        assertThat(adapter.atiende(PlataformaPush.ANDROID)).isFalse();
    }

    // ─── Spec 007, FR-014: el aviso de un habito lleva su etiqueta para no apilarse ──────────

    private static final String ETIQUETA_DE_HABITO = "habito-66507383-7219-43ab-aa42-2fbc76152b82-inicio";

    @Test
    @DisplayName("spec 007: el aviso de habito lleva `tag` para que el navegador reemplace y no apile")
    void elAvisoDeHabitoLlevaTag() {
        WebPushAdapter adapter = new WebPushAdapter("", "", "");

        assertThat(adapter.cuerpoDe(new MensajePush(TipoNotificacion.RECORDATORIO_HABITO, "Tu hábito está por empezar",
                "Meditar: te toca a las 21:00. En juego: 10 puntos.", "/habitos/abc?dimension=BODY",
                ETIQUETA_DE_HABITO)))
                .containsEntry("tag", ETIQUETA_DE_HABITO)
                .containsEntry("title", "Tu hábito está por empezar")
                .containsEntry("data", java.util.Map.of("url", "/habitos/abc?dimension=BODY"));
    }

    @Test
    @DisplayName("spec 007: sin etiqueta el cuerpo web no trae `tag`, como hasta ahora")
    void sinEtiquetaNoHayTag() {
        WebPushAdapter adapter = new WebPushAdapter("", "", "");

        assertThat(adapter.cuerpoDe(new MensajePush(TipoNotificacion.RECORDATORIO_HABITO, "T", "C", "/habitos/abc")))
                .doesNotContainKey("tag");
    }
}
