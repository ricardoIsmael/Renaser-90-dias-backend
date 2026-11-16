package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El tope absoluto de la sesión es una regla pura: dos instantes y una duración (spec 050, tramo E). Sin Spring. */
class TopeDeSesionTest {

    private static final TopeDeSesion NOVENTA_DIAS = TopeDeSesion.de(Duration.ofDays(90));
    private static final Instant CREADA = Instant.parse("2026-08-01T12:00:00Z");

    // spec 050 FR-028
    @Test
    @DisplayName("justo en los 90 días todavía vale; un segundo después, vencida")
    void justoAntesYJustoDespuesDelTope() {
        Instant enElTope = CREADA.plus(Duration.ofDays(90));

        assertThat(NOVENTA_DIAS.vencida(CREADA, enElTope)).as("exactamente 90 días: aún vale").isFalse();
        assertThat(NOVENTA_DIAS.vencida(CREADA, enElTope.minusSeconds(1))).as("un segundo antes").isFalse();
        assertThat(NOVENTA_DIAS.vencida(CREADA, enElTope.plusSeconds(1))).as("un segundo después").isTrue();
        assertThat(NOVENTA_DIAS.vencida(CREADA, CREADA.plus(Duration.ofDays(91)))).as("91 días").isTrue();
    }

    // spec 050 FR-029
    @Test
    @DisplayName("una sesión de 20 días no vence por el tope (los 30 de inactividad son de Spring Session)")
    void unaSesionJovenNoVence() {
        assertThat(NOVENTA_DIAS.vencida(CREADA, CREADA.plus(Duration.ofDays(20)))).isFalse();
        assertThat(NOVENTA_DIAS.vencida(CREADA, CREADA)).isFalse();
    }

    // spec 050 FR-028
    @Test
    @DisplayName("sin tope (valor vacío, cero o negativo) ninguna sesión vence: es la vuelta atrás")
    void sinTopeNadieVence() {
        Instant muyDespues = CREADA.plus(Duration.ofDays(5000));

        assertThat(TopeDeSesion.sinTope().activo()).isFalse();
        assertThat(TopeDeSesion.sinTope().vencida(CREADA, muyDespues)).isFalse();
        assertThat(TopeDeSesion.de(null).vencida(CREADA, muyDespues)).isFalse();
        assertThat(TopeDeSesion.de(Duration.ZERO).vencida(CREADA, muyDespues)).as("0 no es un tope de cero: es sin tope")
                .isFalse();
        assertThat(TopeDeSesion.de(Duration.ofDays(-1)).activo()).isFalse();
    }

    // spec 050 FR-031
    @ParameterizedTest
    @ValueSource(strings = {"2026-10-30T00:00:00Z", "2026-10-30T02:30:00Z", "2026-10-30T04:59:59Z", "2026-10-30T05:00:00Z"})
    @DisplayName("el resultado no depende de la hora del día: con el reloj entre las 00:00 y las 05:00 UTC (el desfase de "
            + "Lima) la cuenta es la misma")
    void conElRelojDeLaMadrugadaUtc(String ahoraTexto) {
        Instant ahora = Instant.parse(ahoraTexto);
        // Creada 90 días y 1 hora antes de «ahora», sin importar de qué lado de la medianoche de Lima o de UTC caiga.
        Instant pasadaDelTope = ahora.minus(Duration.ofDays(90)).minus(Duration.ofHours(1));
        Instant dentroDelTope = ahora.minus(Duration.ofDays(90)).plus(Duration.ofHours(1));

        assertThat(NOVENTA_DIAS.vencida(pasadaDelTope, ahora)).isTrue();
        assertThat(NOVENTA_DIAS.vencida(dentroDelTope, ahora)).isFalse();
    }

    @Test
    @DisplayName("un tope de milisegundos (el 90 sin unidad que Spring lee como 90 ms) impide arrancar en vez de sacar a "
            + "todos")
    void unTopeSinUnidadSeRechaza() {
        assertThatThrownBy(() -> TopeDeSesion.de(Duration.ofMillis(90)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("¿falta la unidad?");
        assertThat(TopeDeSesion.de(Duration.ofMinutes(1)).activo()).as("un minuto sí vale (el E2E lo usa)").isTrue();
    }

    @Test
    @DisplayName("una sesión «creada en el futuro» (reloj adelantado de quien la guardó) no vence")
    void creadaEnElFuturoNoVence() {
        assertThat(NOVENTA_DIAS.vencida(CREADA.plus(Duration.ofDays(3)), CREADA)).isFalse();
    }

    @Test
    @DisplayName("no acepta instantes nulos: un dato que falta no se confunde con «vigente»")
    void sinInstantesNoSeEvalua() {
        assertThatThrownBy(() -> NOVENTA_DIAS.vencida(null, CREADA)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> NOVENTA_DIAS.vencida(CREADA, null)).isInstanceOf(NullPointerException.class);
    }
}
