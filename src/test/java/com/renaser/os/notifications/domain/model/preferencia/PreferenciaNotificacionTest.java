package com.renaser.os.notifications.domain.model.preferencia;

import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferenciaNotificacionTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Test
    void deConstruyeConElHabilitadaPedido() {
        UserId usuario = UserId.of(UUID.randomUUID());

        PreferenciaNotificacion apagada = PreferenciaNotificacion.de(usuario, TipoNotificacion.MENSAJE_MENTOR, false,
                CLOCK);

        assertThat(apagada.habilitada()).isFalse();
        assertThat(apagada.actualizadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void defaultHabilitadaEsTrue() {
        assertThat(PreferenciaNotificacion.DEFAULT_HABILITADA).isTrue();
    }

    @Test
    void rechazaUsuarioNulo() {
        assertThatThrownBy(() -> new PreferenciaNotificacion(null, TipoNotificacion.MENSAJE_MENTOR, true,
                CLOCK.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazaTipoNulo() {
        UserId usuario = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> new PreferenciaNotificacion(usuario, null, true, CLOCK.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
