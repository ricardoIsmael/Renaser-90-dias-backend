package com.renaser.os.habits.domain.model.guia;

import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuiaHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");

    private static ContenidoGuia contenido() {
        return new ContenidoGuia("hacer", "como", "ciencia", "renaser", "alquimia", "resultados", "titulo mantra",
                "intro mantra", "cuerpo mantra", "fuente");
    }

    @Test
    void actualizarContenidoCompletoFijaTextosMantraYFuente() {
        GuiaHabito guia = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), 1,
                AHORA);
        Instant despues = AHORA.plusSeconds(60);

        guia.actualizarContenidoCompleto(contenido(), despues);

        assertThat(guia.queHacer()).isEqualTo("hacer");
        assertThat(guia.comoHacerlo()).isEqualTo("como");
        assertThat(guia.ciencia()).isEqualTo("ciencia");
        assertThat(guia.renaser()).isEqualTo("renaser");
        assertThat(guia.alquimia()).isEqualTo("alquimia");
        assertThat(guia.resultados()).isEqualTo("resultados");
        assertThat(guia.mantraTitulo()).isEqualTo("titulo mantra");
        assertThat(guia.mantraIntro()).isEqualTo("intro mantra");
        assertThat(guia.mantraCuerpo()).isEqualTo("cuerpo mantra");
        assertThat(guia.referenciaFuente()).isEqualTo("fuente");
        assertThat(guia.actualizadoEn()).isEqualTo(despues);
    }

    @Test
    void cerrarEnFijaDiaFin() {
        GuiaHabito guia = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), 10,
                AHORA);

        guia.cerrarEn(20, AHORA.plusSeconds(1));

        assertThat(guia.diaFin()).isEqualTo(20);
        assertThat(guia.aplicaEnDia(20)).isTrue();
        assertThat(guia.aplicaEnDia(21)).isFalse();
    }

    @Test
    void cerrarEnAntesDeDiaInicioFalla() {
        GuiaHabito guia = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), 10,
                AHORA);

        assertThatThrownBy(() -> guia.cerrarEn(5, AHORA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void establecerDiaFinAceptaNullParaDejarlaAbierta() {
        GuiaHabito guia = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), 10,
                AHORA);
        guia.cerrarEn(20, AHORA);

        guia.establecerDiaFin(null, AHORA.plusSeconds(1));

        assertThat(guia.diaFin()).isNull();
        assertThat(guia.aplicaEnDia(90)).isTrue();
    }

    @Test
    void establecerDiaFinAnteriorADiaInicioFalla() {
        GuiaHabito guia = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), 10,
                AHORA);

        assertThatThrownBy(() -> guia.establecerDiaFin(5, AHORA)).isInstanceOf(IllegalArgumentException.class);
    }
}
