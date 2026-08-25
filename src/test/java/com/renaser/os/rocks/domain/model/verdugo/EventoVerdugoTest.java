package com.renaser.os.rocks.domain.model.verdugo;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventoVerdugoTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T21:00:00Z"));

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void registrarConResultadoDeClienteFunciona() {
        EventoVerdugo evento = EventoVerdugo.registrar(participante(), DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(),
                CLOCK.now(), ResultadoVerdugo.COMPLETADO, CLOCK);

        assertThat(evento.resultado()).isEqualTo(ResultadoVerdugo.COMPLETADO);
        assertThat(evento.pendiente()).isFalse();
    }

    @Test
    void rechazaIgnoradoDesdeElCliente() {
        assertThatThrownBy(() -> EventoVerdugo.registrar(participante(), DestinoVerdugo.ROCA_DIARIA,
                UUID.randomUUID(), CLOCK.now(), ResultadoVerdugo.IGNORADO, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolverComoIgnoradoDejaElEventoResuelto() {
        // rehydrate simula un evento pendiente (resultado null) tal como lo dejaria un futuro disparador server-side
        EventoVerdugo pendiente = EventoVerdugo.rehydrate(EventoVerdugoId.newId(), participante(),
                DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(), CLOCK.now(), null, null, CLOCK.now(), CLOCK.now());
        assertThat(pendiente.pendiente()).isTrue();

        pendiente.resolverComoIgnorado(CLOCK);

        assertThat(pendiente.resultado()).isEqualTo(ResultadoVerdugo.IGNORADO);
        assertThat(pendiente.resueltoEn()).isEqualTo(CLOCK.now());
        assertThat(pendiente.pendiente()).isFalse();
    }

    @Test
    void noSePuedeResolverComoIgnoradoDosVeces() {
        EventoVerdugo pendiente = EventoVerdugo.rehydrate(EventoVerdugoId.newId(), participante(),
                DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(), CLOCK.now(), null, null, CLOCK.now(), CLOCK.now());
        pendiente.resolverComoIgnorado(CLOCK);

        assertThatThrownBy(() -> pendiente.resolverComoIgnorado(CLOCK)).isInstanceOf(IllegalStateException.class);
    }
}
