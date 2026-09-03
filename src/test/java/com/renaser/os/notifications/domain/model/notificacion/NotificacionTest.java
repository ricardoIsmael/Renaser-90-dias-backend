package com.renaser.os.notifications.domain.model.notificacion;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificacionTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId usuario() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void emitirCreaUnaNotificacionSinLeerYSinId() {
        Notificacion n = Notificacion.emitir(usuario(), TipoNotificacion.SANTUARIO_ROTO, "Titulo", "Cuerpo", "/ruta",
                CLOCK);

        assertThat(n.id()).isNull();
        assertThat(n.estaLeida()).isFalse();
        assertThat(n.leidaEn()).isNull();
        assertThat(n.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void emitirRechazaTituloVacio() {
        assertThatThrownBy(() -> Notificacion.emitir(usuario(), TipoNotificacion.SANTUARIO_ROTO, "  ", "Cuerpo",
                null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emitirRechazaCuerpoVacio() {
        assertThatThrownBy(() -> Notificacion.emitir(usuario(), TipoNotificacion.SANTUARIO_ROTO, "Titulo", "",
                null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marcarLeidaEsIdempotente() {
        Notificacion n = Notificacion.rehydrate(1L, usuario(), TipoNotificacion.LOGRO_DESBLOQUEADO, "T", "C", null,
                null, CLOCK.now());

        n.marcarLeida(CLOCK);
        Instant primeraLectura = n.leidaEn();
        assertThat(n.estaLeida()).isTrue();

        FixedClock masTarde = FixedClock.at(CLOCK.now().plusSeconds(60));
        n.marcarLeida(masTarde); // repetir no debe mover leidaEn

        assertThat(n.leidaEn()).isEqualTo(primeraLectura);
    }

    @Test
    @DisplayName("C-7: emitir() con origenEventoId lo conserva; sin el, queda null (retrocompatible)")
    void emitirConOrigenEventoIdLoConserva() {
        UUID origenEventoId = UUID.randomUUID();

        Notificacion conOrigen = Notificacion.emitir(usuario(), TipoNotificacion.HITO_PROGRAMA, "T", "C", null,
                origenEventoId, CLOCK);
        Notificacion sinOrigen = Notificacion.emitir(usuario(), TipoNotificacion.HITO_PROGRAMA, "T", "C", null,
                CLOCK);

        assertThat(conOrigen.origenEventoId()).isEqualTo(origenEventoId);
        assertThat(sinOrigen.origenEventoId()).isNull();
    }

    @Test
    @DisplayName("C-7: rehydrate() con origenEventoId lo conserva; el overload de 8 args sigue dando null")
    void rehydrateConOrigenEventoIdLoConserva() {
        UUID origenEventoId = UUID.randomUUID();

        Notificacion conOrigen = Notificacion.rehydrate(1L, usuario(), TipoNotificacion.HITO_PROGRAMA, "T", "C", null,
                null, CLOCK.now(), origenEventoId);
        Notificacion sinOrigen = Notificacion.rehydrate(2L, usuario(), TipoNotificacion.HITO_PROGRAMA, "T", "C", null,
                null, CLOCK.now());

        assertThat(conOrigen.origenEventoId()).isEqualTo(origenEventoId);
        assertThat(sinOrigen.origenEventoId()).isNull();
    }

    @Test
    void perteneceAComparaElUsuarioDueno() {
        UserId dueno = usuario();
        UserId otro = usuario();
        Notificacion n = Notificacion.rehydrate(1L, dueno, TipoNotificacion.LOGRO_DESBLOQUEADO, "T", "C", null, null,
                CLOCK.now());

        assertThat(n.perteneceA(dueno)).isTrue();
        assertThat(n.perteneceA(otro)).isFalse();
    }
}
