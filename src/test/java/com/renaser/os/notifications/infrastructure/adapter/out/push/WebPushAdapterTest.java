package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class WebPushAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Test
    void sinVapidConfiguradoNoRompeLaEmisionNiTocaElCanalNativo() {
        WebPushAdapter adapter = new WebPushAdapter("", "", "");
        TokenPush web = TokenPush.registrar(TokenPushId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "{\"endpoint\":\"https://push.example.test/sub\",\"keys\":{\"p256dh\":\"key\",\"auth\":\"auth\"}}",
                PlataformaPush.WEB, CLOCK);
        TokenPush android = TokenPush.registrar(TokenPushId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "expo-token", PlataformaPush.ANDROID, CLOCK);

        assertThatCode(() -> adapter.enviar(List.of(web, android), "Aviso", "Cuerpo"))
                .doesNotThrowAnyException();
    }
}
