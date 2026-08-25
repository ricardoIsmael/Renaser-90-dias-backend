package com.renaser.os.notifications.domain.model.tokenpush;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenPushTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId usuario() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void registrarAsignaIdYFechas() {
        TokenPush t = TokenPush.registrar(usuario(), "expo-token-123", PlataformaPush.IOS, CLOCK);

        assertThat(t.id()).isNotNull();
        assertThat(t.token()).isEqualTo("expo-token-123");
        assertThat(t.creadoEn()).isEqualTo(CLOCK.now());
        assertThat(t.actualizadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void registrarRechazaTokenVacio() {
        assertThatThrownBy(() -> TokenPush.registrar(usuario(), "  ", PlataformaPush.ANDROID, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarAceptaPlataformaNula() {
        TokenPush t = TokenPush.registrar(usuario(), "token-sin-plataforma", null, CLOCK);
        assertThat(t.plataforma()).isNull();
    }

    @Test
    void reasignarCambiaDuenoYPlataformaSinCambiarElToken() {
        UserId original = usuario();
        UserId nuevo = usuario();
        TokenPush t = TokenPush.registrar(original, "token-compartido", PlataformaPush.IOS, CLOCK);

        FixedClock masTarde = FixedClock.at(CLOCK.now().plusSeconds(3600));
        t.reasignar(nuevo, PlataformaPush.ANDROID, masTarde);

        assertThat(t.usuarioId()).isEqualTo(nuevo);
        assertThat(t.plataforma()).isEqualTo(PlataformaPush.ANDROID);
        assertThat(t.token()).isEqualTo("token-compartido");
        assertThat(t.actualizadoEn()).isEqualTo(masTarde.now());
    }
}
