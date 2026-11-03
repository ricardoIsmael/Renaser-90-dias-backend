package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Lo que el acompañante lee de dónde está parada la persona usa los días de SU programa (spec 022). */
class SituacionConLaDuracionEnElPromptTest {

    private static final LocalDate HOY = LocalDate.of(2027, 3, 1);

    @Test
    @DisplayName("un programa de un año dice «su dia 200 de 365»; uno de 90 sigue diciendo «de 90»")
    void diceLosDiasDeSuPrograma() {
        assertThat(GoogleGenAiRenasiaChatAdapter.formatearSituacion(new SituacionDelAprendiz(200, 3, HOY).conDuracion(365)))
                .contains("su dia 200 de 365, en la fase 3 de 4");
        assertThat(GoogleGenAiRenasiaChatAdapter.formatearSituacion(new SituacionDelAprendiz(17, 2, HOY)))
                .contains("su dia 17 de 90, en la fase 2 de 4");
    }

    @Test
    @DisplayName("conservar lo demás al fijar la duración: el día, la fase y la fecha no cambian")
    void conDuracionNoPierdeNada() {
        SituacionDelAprendiz base = new SituacionDelAprendiz(120, 2, HOY);
        SituacionDelAprendiz anual = base.conDuracion(365);
        assertThat(anual.duracionDias()).isEqualTo(365);
        assertThat(anual.diaPrograma()).isEqualTo(120);
        assertThat(anual.fase()).isEqualTo(2);
        assertThat(anual.hoy()).isEqualTo(HOY);
        assertThat(base.duracionDias()).isEqualTo(90);
        assertThat(anual.conMapa(null).duracionDias()).isEqualTo(365);
        assertThat(anual.conTrato(null).duracionDias()).isEqualTo(365);
    }
}
