package com.renaser.os.users.domain.model.user;

import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La gracia de 30 dias de D-243 se mide en instantes, no en fechas de ninguna zona (regla 02). El
 * cierre de estas pruebas cae a las 03:00 UTC, que en Lima es todavia el DIA ANTERIOR (22:00): si el
 * plazo se contara en dias calendario del servidor (UTC) o de Lima, el borrado se correria horas.
 */
class PlazoDeGraciaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final PlazoDeGracia TREINTA_DIAS = new PlazoDeGracia(30);
    /** 2026-10-01 03:00 UTC = 2026-09-30 22:00 en Lima. */
    private static final Instant CIERRE = Instant.parse("2026-10-01T03:00:00Z");

    @Test
    @DisplayName("el instante de cierre de la prueba cae en Lima el dia anterior al de UTC")
    void elFixtureCaeElDiaAnteriorEnLima() {
        assertThat(CIERRE.atZone(LIMA).toLocalDate()).isEqualTo(java.time.LocalDate.parse("2026-09-30"));
        assertThat(CIERRE.atZone(ZoneId.of("UTC")).toLocalDate()).isEqualTo(java.time.LocalDate.parse("2026-10-01"));
    }

    @Test
    @DisplayName("se borra exactamente 30 x 24 h despues del cierre")
    void seBorraTreintaDiasExactosDespues() {
        assertThat(TREINTA_DIAS.seBorraEl(CIERRE)).isEqualTo(Instant.parse("2026-10-31T03:00:00Z"));
        assertThat(TREINTA_DIAS.seBorraEl(null)).isNull();
    }

    @Test
    @DisplayName("un segundo antes de cumplirse no entra en el corte; al cumplirse, si")
    void elCorteRespetaElInstante() {
        FixedClock unSegundoAntes = FixedClock.at(Instant.parse("2026-10-31T02:59:59Z"));
        FixedClock justo = FixedClock.at(Instant.parse("2026-10-31T03:00:00Z"));

        assertThat(CIERRE.isAfter(TREINTA_DIAS.corteParaBorrar(unSegundoAntes.now()))).isTrue();
        assertThat(CIERRE.isAfter(TREINTA_DIAS.corteParaBorrar(justo.now()))).isFalse();
    }

    @Test
    @DisplayName("el estado informa la fecha de borrado y los dias que faltan, redondeados hacia arriba")
    void estadoDeUnaCuentaCerrada() {
        var estado = TREINTA_DIAS.estadoDe(CIERRE, Instant.parse("2026-10-30T04:00:00Z"));

        assertThat(estado.bajaPendiente()).isTrue();
        assertThat(estado.purgaEl()).isEqualTo(Instant.parse("2026-10-31T03:00:00Z"));
        assertThat(estado.diasRestantes()).isEqualTo(1L);
        assertThat(estado.diasDeGracia()).isEqualTo(30);
    }

    @Test
    @DisplayName("un plazo de cero dias no es un plazo")
    void plazoInvalido() {
        assertThatThrownBy(() -> new PlazoDeGracia(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
