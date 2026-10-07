package com.renaser.os.chat.domain.model.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Aprendiz;
import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Puesto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Quiénes salen en el podio (D-262). */
class PodioDeLaSemanaTest {

    @Test
    @DisplayName("tres en el podio y el 4.º y el 5.º debajo; el 6.º no sale")
    void tresYDos() {
        PodioDeLaSemana podio = PodioDeLaSemana.de(List.of(a("Liz Mendoza", "96.4"), a("Jorge Pérez", "92.1"),
                a("Carmen Rojas", "88.7"), a("Rosa Torres", "85.0"), a("Miguel Alva", "83.2"), a("Sexto Puesto", "80")));

        assertThat(podio.podio()).extracting(Puesto::lugar, Puesto::nombre)
                .containsExactly(t(1, "Liz M."), t(2, "Jorge P."), t(3, "Carmen R."));
        assertThat(podio.debajo()).extracting(Puesto::lugar, Puesto::nombre)
                .containsExactly(t(4, "Rosa T."), t(5, "Miguel A."));
        assertThat(podio.todos()).hasSize(5);
    }

    @Test
    @DisplayName("los empates comparten puesto, como en una competencia: 1, 1, 3, 3, 5")
    void empates() {
        PodioDeLaSemana podio = PodioDeLaSemana.de(List.of(a("Ana Q", "90"), a("Beto R", "90.0"), a("Carla D", "70"),
                a("Dora E", "70"), a("Eva F", "60")));

        assertThat(podio.todos()).extracting(Puesto::lugar).containsExactly(1, 1, 3, 3, 5);
        assertThat(podio.podio()).extracting(Puesto::nombre).containsExactly("Ana Q.", "Beto R.", "Carla D.");
    }

    @Test
    @DisplayName("solo con puntaje: los ceros no salen, y con menos de tres se publica con los que haya")
    void soloConPuntaje() {
        PodioDeLaSemana podio = PodioDeLaSemana.de(List.of(a("Ana Q", "12.5"), a("Beto R", "0"), a("Carla D", "0.0")));

        assertThat(podio.estaVacio()).isFalse();
        assertThat(podio.podio()).extracting(Puesto::nombre).containsExactly("Ana Q.");
        assertThat(podio.debajo()).isEmpty();
    }

    @Test
    @DisplayName("si nadie tiene puntaje, el podio está vacío")
    void nadie() {
        assertThat(PodioDeLaSemana.de(List.of(a("Ana Q", "0"), a("Beto R", "0"))).estaVacio()).isTrue();
        assertThat(PodioDeLaSemana.de(List.of()).estaVacio()).isTrue();
    }

    @Test
    @DisplayName("si la lista llega desordenada, se ordena por puntaje; entre empatados se respeta el orden que llegó")
    void ordena() {
        PodioDeLaSemana podio = PodioDeLaSemana.de(List.of(a("Ana Q", "50"), a("Beto R", "80"), a("Carla D", "50")));

        assertThat(podio.podio()).extracting(Puesto::nombre).containsExactly("Beto R.", "Ana Q.", "Carla D.");
        assertThat(podio.podio()).extracting(Puesto::lugar).containsExactly(1, 2, 2);
    }

    private static Aprendiz a(String nombre, String puntaje) {
        return new Aprendiz(nombre, new BigDecimal(puntaje));
    }

    private static org.assertj.core.groups.Tuple t(Object... valores) {
        return org.assertj.core.groups.Tuple.tuple(valores);
    }
}
