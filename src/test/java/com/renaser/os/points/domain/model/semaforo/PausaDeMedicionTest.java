package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PausaDeMedicionTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 22);
    private static final Instant AHORA = Instant.parse("2026-09-22T15:00:00Z");

    private static PausaDeMedicion pausaHasta(LocalDate hasta) {
        return PausaDeMedicion.iniciar(PausaId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), HOY, hasta, AHORA);
    }

    @Test
    void cubreDesdeHoyHastaLaFechaElegidaInclusive() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(3));

        assertThat(pausa.cubre(HOY.minusDays(1))).isFalse();
        assertThat(pausa.cubre(HOY)).isTrue();
        assertThat(pausa.cubre(HOY.plusDays(3))).isTrue();
        assertThat(pausa.cubre(HOY.plusDays(4))).isFalse();
    }

    @Test
    void noSePuedeElegirUnaFechaPasada() {
        assertThatThrownBy(() -> pausaHasta(HOY.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alPasarLaFechaVuelveASolo() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(2));

        assertThat(pausa.vigenteEl(HOY.plusDays(2))).isTrue();
        assertThat(pausa.vigenteEl(HOY.plusDays(3))).isFalse();
    }

    /** Reanudar el mismo día que se pausó: ningún día quedó sin medir. */
    @Test
    void reanudarElMismoDiaNoDejaDiasSinMedir() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(5));

        pausa.reanudar(HOY, AHORA);

        assertThat(pausa.cubre(HOY)).isFalse();
        assertThat(pausa.vigenteEl(HOY)).isFalse();
    }

    @Test
    void reanudarAntesDeTiempoMideDesdeEseDia() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(5));

        pausa.reanudar(HOY.plusDays(2), AHORA.plusSeconds(172_800));

        assertThat(pausa.cubre(HOY.plusDays(1))).isTrue();
        assertThat(pausa.cubre(HOY.plusDays(2))).isFalse();
        assertThat(pausa.ultimoDiaPausado()).isEqualTo(HOY.plusDays(1));
    }

    @Test
    void seLePuedeCambiarLaFechaMientrasSigue() {
        PausaDeMedicion pausa = pausaHasta(HOY.plusDays(2));

        pausa.cambiarHasta(HOY.plusDays(9), HOY.plusDays(1));

        assertThat(pausa.cubre(HOY.plusDays(9))).isTrue();
    }

    @Test
    void unaPausaTerminadaNoSeTocaMas() {
        PausaDeMedicion pausa = pausaHasta(HOY);
        pausa.reanudar(HOY, AHORA);

        assertThatThrownBy(() -> pausa.cambiarHasta(HOY.plusDays(3), HOY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pausa.reanudar(HOY, AHORA)).isInstanceOf(IllegalStateException.class);
    }
}
