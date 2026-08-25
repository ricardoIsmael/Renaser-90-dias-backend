package com.renaser.os.onboarding.domain.model.cuestionario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PreguntaTest {

    @Test
    @DisplayName("esCondicional() es true solo si tiene preguntaPadreId")
    void esCondicionalReflejaElPadre() {
        Pregunta sinPadre = new Pregunta(1, (short) 1, "k1", "texto", TipoPreguntaOnboarding.TEXTO, null, false,
                (short) 0, null, null, Instant.now());
        Pregunta conPadre = new Pregunta(2, (short) 1, "k2", "texto", TipoPreguntaOnboarding.TEXTO, null, false,
                (short) 0, null, 1, Instant.now());

        assertThat(sinPadre.esCondicional()).isFalse();
        assertThat(conPadre.esCondicional()).isTrue();
    }
}
