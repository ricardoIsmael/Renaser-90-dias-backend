package com.renaser.os.calendar.domain.model.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 053: «Sesión personalizada» tiene los avisos de la sesión especial y ninguna elegibilidad extra. */
class SesionPersonalizadaTest {

    // spec 053 FR-015
    @Test
    @DisplayName("FR-015: sin avisos propios, la sesión personalizada avisa 1 día, 1 hora, 30 y 10 minutos antes, y no exige verde")
    void avisosYElegibilidad() {
        assertThat(ReglasPorTipoEvento.recordatoriosPorDefecto(TipoEvento.SESION_PERSONALIZADA))
                .containsExactly(ReglaRecordatorio.diasAntes(1, 1), ReglaRecordatorio.minutosAntes(2, 60),
                        ReglaRecordatorio.minutosAntes(3, 30), ReglaRecordatorio.minutosAntes(4, 10));
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

    // spec 053 FR-013
    @Test
    @DisplayName("FR-013: el título de la sesión personalizada admite 60 letras («Sesión personalizada · <nombre>»); los demás tipos, 30")
    void tituloMasLargo() {
        String largo = "Sesión personalizada · Ana María Fernández";
        assertThat(largo.length()).isBetween(31, 60);
        var ahora = com.renaser.os.shared.domain.FixedClock.at(java.time.Instant.parse("2026-11-14T15:00:00Z"));
        java.util.function.Function<TipoEvento, Evento> crear = tipo -> Evento.crear(EventoId.of(java.util.UUID.randomUUID()), largo, null,
                ahora.now().plusSeconds(3600), 60, java.time.ZoneId.of("America/Lima"), TipoUbicacion.DIRECCION, "Av. Lima 1", TipoAudiencia.TODOS, null,
                null, null, tipo, false, false, false, null, java.util.Set.of(), java.util.List.of(),
                com.renaser.os.shared.domain.UserId.of(java.util.UUID.randomUUID()), ahora, com.renaser.os.shared.domain.Formacion.POR_DEFECTO, null);

        assertThat(crear.apply(TipoEvento.SESION_PERSONALIZADA).titulo()).isEqualTo(largo);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> crear.apply(TipoEvento.SESION_ESPECIAL)).isInstanceOf(IllegalArgumentException.class);
    }
}
