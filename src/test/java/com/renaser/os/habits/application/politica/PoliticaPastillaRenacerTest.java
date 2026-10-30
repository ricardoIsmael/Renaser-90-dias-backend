package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E-652 (issue #33): la Pastilla Renacer no se marca hecha con el gesto generico. */
class PoliticaPastillaRenacerTest {

    private final PoliticaPastillaRenacer politica = new PoliticaPastillaRenacer();
    /** La politica no mira el habito: basta con que no sea null en la firma. */
    private final Habito habito = null;

    @Test
    @DisplayName("selecciona el hábito PASTILLA_RENACER, por clave y no por tipo")
    void seleccionaPastillaRenacerPorClave() {
        assertThat(politica.selector()).isEqualTo(SelectorHabito.porClave("PASTILLA_RENACER"));
    }

    @Test
    @DisplayName("nunca procede por el gesto generico, y el motivo dice por donde SI se hace")
    void nuncaProcede() {
        DecisionPolitica decision = politica.puedeCompletarseDirecto(habito, ContextoCompletar.sinHechosExternos());

        assertThat(decision).isInstanceOfSatisfying(DecisionPolitica.NoProcede.class, no -> assertThat(no.motivo())
                .isEqualTo("Para la Pastilla Renacer, escucha el audio y responde las dos preguntas."));
    }

    @Test
    @DisplayName("ni siquiera con texto de respuestas ya subido: su unico camino es /spirit-audio/submit")
    void ningunTextoLaAbre() {
        DecisionPolitica decision = politica.puedeCompletarseDirecto(habito,
                ContextoCompletar.de(() -> false, () -> true, null));

        assertThat(decision).isInstanceOf(DecisionPolitica.NoProcede.class);
    }
}
