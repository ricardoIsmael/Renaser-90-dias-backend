package com.renaser.os.calendar.domain.model.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 053: «Sesión personalizada» tiene los avisos de la sesión especial y ninguna elegibilidad extra. */
class SesionPersonalizadaTest {

    // spec 053 FR-002
    @Test
    @DisplayName("FR-002: sin avisos propios, la sesión personalizada avisa a las 6:00 del día y 10 minutos antes, y no exige verde")
    void avisosYElegibilidad() {
        assertThat(ReglasPorTipoEvento.recordatoriosPorDefecto(TipoEvento.SESION_PERSONALIZADA))
                .isEqualTo(ReglasPorTipoEvento.recordatoriosPorDefecto(TipoEvento.SESION_ESPECIAL))
                .contains(ReglaRecordatorio.horaDelDia(1, LocalTime.of(6, 0)), ReglaRecordatorio.minutosAntes(2, 10));
        assertThat(ReglasPorTipoEvento.requiereElegibilidad(TipoEvento.SESION_PERSONALIZADA)).isFalse();
    }

    // spec 053 FR-002
    @Test
    @DisplayName("FR-002: el tipo viaja con su nombre tal cual en dominio, base y wire")
    void elLiteralEsElMismoEnTodasPartes() {
        assertThat(TipoEvento.valueOf("SESION_PERSONALIZADA")).isEqualTo(TipoEvento.SESION_PERSONALIZADA);
        assertThat(com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento.TipoEventoJpa.valueOf(TipoEvento.SESION_PERSONALIZADA.name()))
                .isNotNull();
    }
}
