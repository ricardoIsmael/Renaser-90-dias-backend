package com.renaser.os.rocks.domain.model.rocasemanal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class VentanaPlanificacionSemanalTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    @Test
    @DisplayName("domingo antes de las 12:00 esta cerrada")
    void domingoAntesDelMediodiaCerrada() {
        Instant domingoTemprano = Instant.parse("2026-08-23T11:59:00Z"); // domingo
        assertThat(VentanaPlanificacionSemanal.abierta(domingoTemprano, UTC)).isFalse();
    }

    @Test
    @DisplayName("domingo desde las 12:00 esta abierta")
    void domingoDesdeElMediodiaAbierta() {
        Instant domingoMediodia = Instant.parse("2026-08-23T12:00:00Z");
        assertThat(VentanaPlanificacionSemanal.abierta(domingoMediodia, UTC)).isTrue();
    }

    @Test
    @DisplayName("lunes antes de las 09:00 sigue abierta")
    void lunesAntesDeLas9Abierta() {
        Instant lunesTemprano = Instant.parse("2026-08-24T08:59:00Z");
        assertThat(VentanaPlanificacionSemanal.abierta(lunesTemprano, UTC)).isTrue();
    }

    @Test
    @DisplayName("lunes a las 09:00 en punto ya cerro")
    void lunesALas9EnPuntoCerrada() {
        Instant lunes9 = Instant.parse("2026-08-24T09:00:00Z");
        assertThat(VentanaPlanificacionSemanal.abierta(lunes9, UTC)).isFalse();
    }

    @Test
    @DisplayName("martes a cualquier hora esta cerrada")
    void martesCerrada() {
        Instant martes = Instant.parse("2026-08-25T15:00:00Z");
        assertThat(VentanaPlanificacionSemanal.abierta(martes, UTC)).isFalse();
    }

    @Test
    @DisplayName("creado en plazo, fuera de ventana ahora: no editable")
    void creadoEnPlazoFueraDeVentanaNoEditable() {
        Instant creado = Instant.parse("2026-08-23T13:00:00Z"); // domingo, en plazo
        Instant ahora = Instant.parse("2026-08-25T10:00:00Z"); // martes, ventana cerrada
        assertThat(VentanaPlanificacionSemanal.puedeEditar(EstadoPlazo.EN_PLAZO, creado, ahora, UTC)).isFalse();
    }

    @Test
    @DisplayName("creado en plazo, todavia dentro de la ventana: editable")
    void creadoEnPlazoDentroDeVentanaEditable() {
        Instant creado = Instant.parse("2026-08-23T13:00:00Z");
        Instant ahora = Instant.parse("2026-08-24T08:00:00Z"); // lunes antes de las 9
        assertThat(VentanaPlanificacionSemanal.puedeEditar(EstadoPlazo.EN_PLAZO, creado, ahora, UTC)).isTrue();
    }

    @Test
    @DisplayName("a destiempo, dentro de las 2h: editable")
    void aDestiempoDentroDeLas2HorasEditable() {
        Instant creado = Instant.parse("2026-08-25T10:00:00Z"); // martes, a destiempo
        Instant ahora = creado.plusSeconds(3600); // +1h
        assertThat(VentanaPlanificacionSemanal.puedeEditar(EstadoPlazo.A_DESTIEMPO, creado, ahora, UTC)).isTrue();
    }

    @Test
    @DisplayName("a destiempo, pasadas las 2h: no editable")
    void aDestiempoPasadasLas2HorasNoEditable() {
        Instant creado = Instant.parse("2026-08-25T10:00:00Z");
        Instant ahora = creado.plusSeconds(7201); // +2h y 1s
        assertThat(VentanaPlanificacionSemanal.puedeEditar(EstadoPlazo.A_DESTIEMPO, creado, ahora, UTC)).isFalse();
    }

    @Test
    @DisplayName("a destiempo cerca de medianoche: el margen no cruza al dia siguiente")
    void aDestiempoNoCruzaMedianoche() {
        Instant creado = Instant.parse("2026-08-25T23:00:00Z"); // martes 23:00, a destiempo
        Instant limite = VentanaPlanificacionSemanal.limiteTardio(creado, UTC);
        assertThat(limite).isEqualTo(Instant.parse("2026-08-25T23:59:59.999999999Z"));
    }

    @Test
    @DisplayName("caso mixto: creado a destiempo el sabado, pero la ventana normal ya abrio el domingo")
    void casoMixtoVentanaNormalManda() {
        Instant creadoSabado = Instant.parse("2026-08-22T10:00:00Z"); // sabado, a destiempo
        Instant domingoTarde = Instant.parse("2026-08-23T13:00:00Z"); // domingo 13:00, ventana abierta
        assertThat(VentanaPlanificacionSemanal.puedeEditar(EstadoPlazo.A_DESTIEMPO, creadoSabado, domingoTarde, UTC))
                .isTrue();
    }
}
