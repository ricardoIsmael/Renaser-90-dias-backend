package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** E-652 (issue #33): la Audioterapia semanal solo se cierra con el texto de sus dos respuestas ya subido. */
class PoliticaAudioterapiaTest {

    private final PoliticaAudioterapia politica = new PoliticaAudioterapia();
    /** La politica no mira el habito: basta con que no sea null en la firma. */
    private final Habito habito = null;

    @Test
    @DisplayName("selecciona el hábito AUDIO_THERAPY_WEEKLY, por clave y no por tipo")
    void seleccionaAudioterapiaPorClave() {
        assertThat(politica.selector()).isEqualTo(SelectorHabito.porClave("AUDIO_THERAPY_WEEKLY"));
    }

    @Test
    @DisplayName("el largo mínimo es el de la app: dos respuestas de al menos 15 caracteres")
    void elLargoMinimoEsElDeLaApp() {
        assertThat(PoliticaAudioterapia.LARGO_MINIMO_DEL_TEXTO).isEqualTo(30);
    }

    @Test
    @DisplayName("sin el texto de las respuestas no procede, y el motivo dice por donde SI se hace")
    void sinRespuestasNoProcede() {
        DecisionPolitica decision = politica.puedeCompletarseDirecto(habito,
                ContextoCompletar.de(() -> false, () -> false, null));

        assertThat(decision).isInstanceOfSatisfying(DecisionPolitica.NoProcede.class, no -> assertThat(no.motivo())
                .isEqualTo("Para la Audioterapia semanal, escucha el audio y responde las dos preguntas."));
    }

    @Test
    @DisplayName("con el texto de las respuestas ya subido procede")
    void conRespuestasProcede() {
        DecisionPolitica decision = politica.puedeCompletarseDirecto(habito,
                ContextoCompletar.de(() -> false, () -> true, null));

        assertThat(decision).isInstanceOf(DecisionPolitica.Procede.class);
    }

    @Test
    @DisplayName("pregunta por las respuestas una sola vez (perezoso y memoizado) y nunca por el Muro")
    void consultaUnaSolaVez() {
        AtomicInteger consultas = new AtomicInteger();
        ContextoCompletar contexto = ContextoCompletar.de(() -> {
            throw new AssertionError("la Audioterapia no mira el Muro");
        }, () -> consultas.incrementAndGet() > 0, null);

        politica.puedeCompletarseDirecto(habito, contexto);
        politica.puedeCompletarseDirecto(habito, contexto);

        assertThat(consultas).hasValue(1);
    }
}
