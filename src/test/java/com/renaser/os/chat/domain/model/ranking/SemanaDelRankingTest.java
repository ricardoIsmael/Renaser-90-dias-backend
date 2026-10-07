package com.renaser.os.chat.domain.model.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La semana del podio (D-262): lunes a domingo ya cerrada, en hora de Lima. */
class SemanaDelRankingTest {

    private static final LocalDate LUNES_28 = LocalDate.of(2026, 9, 28);

    @Test
    @DisplayName("el lunes 5 a las 09:00 de Lima la última semana cerrada es la del 28 de septiembre al 4 de octubre")
    void elLunesALasNueve() {
        SemanaDelRanking semana = SemanaDelRanking.ultimaCerradaAl(Instant.parse("2026-10-05T14:00:00Z"));

        assertThat(semana.lunes()).isEqualTo(LUNES_28);
        assertThat(semana.domingo()).isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    @DisplayName("madrugada UTC del lunes = domingo por la noche en Lima: esa semana todavía no cerró")
    void madrugadaUtcEsDomingoEnLima() {
        // 03:00 UTC del lunes 5 = 22:00 del domingo 4 en Lima. Con la fecha del servidor (lunes 5) saldría la
        // semana del 28, que en Lima todavía está corriendo.
        SemanaDelRanking semana = SemanaDelRanking.ultimaCerradaAl(Instant.parse("2026-10-05T03:00:00Z"));

        assertThat(semana.lunes()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(semana.domingo()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    @Test
    @DisplayName("la medianoche de Lima (05:00 UTC) ya cierra la semana; y todo el lunes y el resto de la semana dan la misma")
    void desdeLaMedianocheDeLima() {
        assertThat(SemanaDelRanking.ultimaCerradaAl(Instant.parse("2026-10-05T05:00:00Z")).lunes()).isEqualTo(LUNES_28);
        assertThat(SemanaDelRanking.ultimaCerradaAl(Instant.parse("2026-10-06T04:59:00Z")).lunes()).isEqualTo(LUNES_28);
        assertThat(SemanaDelRanking.ultimaCerradaAl(Instant.parse("2026-10-12T04:59:00Z")).lunes()).isEqualTo(LUNES_28);
    }

    @Test
    @DisplayName("el rango en español, para la imagen")
    void rango() {
        assertThat(new SemanaDelRanking(LUNES_28).rango()).isEqualTo("Del lunes 28 de septiembre al domingo 4 de octubre");
    }

    @Test
    @DisplayName("los ids salen de la semana: los mismos al recalcular, distintos entre piezas y entre semanas")
    void idsDerivados() {
        SemanaDelRanking semana = new SemanaDelRanking(LUNES_28);
        SemanaDelRanking siguiente = new SemanaDelRanking(LUNES_28.plusWeeks(1));

        assertThat(semana.idDelTexto()).isEqualTo(new SemanaDelRanking(LUNES_28).idDelTexto());
        assertThat(semana.idDelTexto()).isNotEqualTo(semana.idDeLaImagen());
        assertThat(semana.idDelTexto()).isNotEqualTo(siguiente.idDelTexto());
        assertThat(semana.rutaDeLaImagen()).isEqualTo("ranking-semanal/2026-09-28-v1.png");
    }

    @Test
    @DisplayName("una semana que no empieza en lunes no existe")
    void soloLunes() {
        assertThatThrownBy(() -> new SemanaDelRanking(LocalDate.of(2026, 9, 29)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
