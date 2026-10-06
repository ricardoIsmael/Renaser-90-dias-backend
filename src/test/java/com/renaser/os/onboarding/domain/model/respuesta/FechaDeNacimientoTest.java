package com.renaser.os.onboarding.domain.model.respuesta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-80 (2026-10-06): solo mayores de 18, con el día de Lima. El reloj se fija en la madrugada UTC
 * (regla 02 §3): a las 02:00 UTC del 7 de octubre de 2026, en Lima todavía es el 6.
 */
class FechaDeNacimientoTest {

    private static final Instant MADRUGADA_UTC_DEL_7 = Instant.parse("2026-10-07T02:00:00Z");
    private static final Instant MEDIODIA_DEL_7 = Instant.parse("2026-10-07T17:00:00Z");

    @Test
    @DisplayName("17 años y 364 días: se rechaza con «Renaser es solo para mayores de 18 años»")
    void diecisieteAniosYTrescientosSesentaYCuatroDias() {
        assertThatThrownBy(() -> FechaDeNacimiento.requireMayorDeEdad("2008-10-07", MADRUGADA_UTC_DEL_7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Renaser es solo para mayores de 18 años");
    }

    @Test
    @DisplayName("18 justos hoy en Lima: entra")
    void dieciochoJustos() {
        assertThatCode(() -> FechaDeNacimiento.requireMayorDeEdad("2008-10-06", MADRUGADA_UTC_DEL_7))
                .doesNotThrowAnyException();
        assertThatCode(() -> FechaDeNacimiento.requireMayorDeEdad("1995-06-15", MADRUGADA_UTC_DEL_7))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el día es el de Lima, no el de UTC: el 7 en UTC todavía no le alcanza a quien cumple el 7")
    void elDiaEsElDeLima() {
        assertThatThrownBy(() -> FechaDeNacimiento.requireMayorDeEdad("2008-10-07", MADRUGADA_UTC_DEL_7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> FechaDeNacimiento.requireMayorDeEdad("2008-10-07", MEDIODIA_DEL_7))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("lo que la app vieja dejaba elegir (14 a 17 años) y una fecha futura se rechazan")
    void menoresYFuturo() {
        assertThatThrownBy(() -> FechaDeNacimiento.requireMayorDeEdad("2012-06-15", MEDIODIA_DEL_7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FechaDeNacimiento.requireMayorDeEdad("2030-01-01", MEDIODIA_DEL_7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("quien nació un 29 de febrero cumple el 1 de marzo en años no bisiestos")
    void veintinueveDeFebrero() {
        assertThatThrownBy(() -> FechaDeNacimiento.requireMayorDeEdad("2008-02-29", Instant.parse("2026-02-28T17:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> FechaDeNacimiento.requireMayorDeEdad("2008-02-29", Instant.parse("2026-03-01T17:00:00Z")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un texto que no es una fecha ISO se deja pasar, como antes (validar el formato es otra regla)")
    void textoQueNoEsFecha() {
        assertThatCode(() -> FechaDeNacimiento.requireMayorDeEdad("15/06/2012", MEDIODIA_DEL_7))
                .doesNotThrowAnyException();
        assertThatCode(() -> FechaDeNacimiento.requireMayorDeEdad(null, MEDIODIA_DEL_7))
                .doesNotThrowAnyException();
    }
}
