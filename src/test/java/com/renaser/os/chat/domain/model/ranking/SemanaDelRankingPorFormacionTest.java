package com.renaser.os.chat.domain.model.ranking;

import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** El podio semanal es uno por formación (spec 024, FR-040): ids y rutas propios, y los de la Escuela son los de siempre. */
class SemanaDelRankingPorFormacionTest {

    private static final SemanaDelRanking SEMANA = new SemanaDelRanking(LocalDate.of(2026, 10, 26));

    @Test
    @DisplayName("Los ids de la Escuela son los de antes de las formaciones: el podio de la semana que ya salió no se repite al desplegar")
    void laEscuelaConservaSusIds() {
        assertThat(SEMANA.idDelTexto(Formacion.ESCUELA)).isEqualTo(SEMANA.idDelTexto());
        assertThat(SEMANA.idDeLaImagen(Formacion.ESCUELA)).isEqualTo(SEMANA.idDeLaImagen());
        assertThat(SEMANA.rutaDeLaImagen(Formacion.ESCUELA)).isEqualTo(SEMANA.rutaDeLaImagen()).isEqualTo("ranking-semanal/2026-10-26-v1.jpg");
    }

    @Test
    @DisplayName("Cada formación tiene sus propios ids y su propia ruta: dos podios de la misma semana no chocan")
    void cadaFormacionTieneLosSuyos() {
        assertThat(SEMANA.idDelTexto(Formacion.DIPLOMADO)).isNotEqualTo(SEMANA.idDelTexto(Formacion.ESCUELA))
                .isNotEqualTo(SEMANA.idDelTexto(Formacion.CERTIFICACION));
        assertThat(SEMANA.idDeLaImagen(Formacion.DIPLOMADO)).isNotEqualTo(SEMANA.idDelTexto(Formacion.DIPLOMADO));
        assertThat(SEMANA.rutaDeLaImagen(Formacion.DIPLOMADO)).isEqualTo("ranking-semanal/diplomado/2026-10-26-v1.jpg");
        assertThat(SEMANA.idDelTexto(Formacion.DIPLOMADO)).isEqualTo(SEMANA.idDelTexto(Formacion.DIPLOMADO));
    }
}
