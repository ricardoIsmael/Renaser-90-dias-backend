package com.renaser.os.users.domain.model.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de dominio puro (sin Spring, sin Postgres) — portado 1:1 de
 * features/account-deletion/plazo.test.ts (backend viejo).
 */
class EstadoBajaCuentaTest {

    private static final int DIAS_DE_GRACIA = 14;

    @Test
    @DisplayName("sin solicitud: bajaPendiente false y todo lo demas null")
    void sinSolicitud() {
        EstadoBajaCuenta estado = EstadoBajaCuenta.sinSolicitud(DIAS_DE_GRACIA);

        assertThat(estado.bajaPendiente()).isFalse();
        assertThat(estado.solicitadaEn()).isNull();
        assertThat(estado.purgaEl()).isNull();
        assertThat(estado.diasRestantes()).isNull();
        assertThat(estado.diasDeGracia()).isEqualTo(DIAS_DE_GRACIA);
    }

    @Test
    @DisplayName("de(null, ...) delega en sinSolicitud")
    void deConSolicitadaEnNuloEquivaleASinSolicitud() {
        Instant ahora = Instant.parse("2026-08-26T10:00:00Z");

        EstadoBajaCuenta estado = EstadoBajaCuenta.de(null, ahora, DIAS_DE_GRACIA);

        assertThat(estado).isEqualTo(EstadoBajaCuenta.sinSolicitud(DIAS_DE_GRACIA));
    }

    @Test
    @DisplayName("recien solicitada: purgaEl = solicitadaEn + diasDeGracia, diasRestantes = diasDeGracia")
    void recienSolicitada() {
        Instant solicitadaEn = Instant.parse("2026-08-26T10:00:00Z");

        EstadoBajaCuenta estado = EstadoBajaCuenta.de(solicitadaEn, solicitadaEn, DIAS_DE_GRACIA);

        assertThat(estado.bajaPendiente()).isTrue();
        assertThat(estado.purgaEl()).isEqualTo(solicitadaEn.plusSeconds(DIAS_DE_GRACIA * 86400L));
        assertThat(estado.diasRestantes()).isEqualTo(DIAS_DE_GRACIA);
    }

    @Test
    @DisplayName("diasRestantes se redondea HACIA ARRIBA: 30 minutos antes de purgar sigue siendo 1 dia, no 0")
    void diasRestantesRedondeaHaciaArriba() {
        Instant solicitadaEn = Instant.parse("2026-08-26T10:00:00Z");
        Instant purgaEl = solicitadaEn.plusSeconds(DIAS_DE_GRACIA * 86400L);
        Instant treintaMinutosAntes = purgaEl.minusSeconds(1800);

        EstadoBajaCuenta estado = EstadoBajaCuenta.de(solicitadaEn, treintaMinutosAntes, DIAS_DE_GRACIA);

        assertThat(estado.diasRestantes()).isEqualTo(1L);
    }

    @Test
    @DisplayName("gracia vencida: diasRestantes es null (el cron la purgara en su proxima pasada)")
    void graciaVencida() {
        Instant solicitadaEn = Instant.parse("2026-08-01T00:00:00Z");
        Instant muchoDespues = solicitadaEn.plusSeconds((DIAS_DE_GRACIA + 5) * 86400L);

        EstadoBajaCuenta estado = EstadoBajaCuenta.de(solicitadaEn, muchoDespues, DIAS_DE_GRACIA);

        assertThat(estado.bajaPendiente()).isTrue();
        assertThat(estado.diasRestantes()).isNull();
    }

    @Test
    @DisplayName("exactamente en el instante de purga: vencida (restanteMs <= 0)")
    void exactamenteEnElInstanteDePurgaEsVencida() {
        Instant solicitadaEn = Instant.parse("2026-08-01T00:00:00Z");
        Instant purgaEl = solicitadaEn.plusSeconds(DIAS_DE_GRACIA * 86400L);

        EstadoBajaCuenta estado = EstadoBajaCuenta.de(solicitadaEn, purgaEl, DIAS_DE_GRACIA);

        assertThat(estado.diasRestantes()).isNull();
    }
}
