package com.renaser.os.chat.domain.model.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Aprendiz;
import com.renaser.os.chat.domain.model.ranking.TextoDelPodio.Plantilla;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El texto debajo de la imagen (D-262): las tres plantillas aprobadas, su rotación y cómo se adaptan. */
class TextoDelPodioTest {

    private static final PodioDeLaSemana TRES = podio(a("Liz Mendoza", "96.4"), a("Jorge Pérez", "92.1"),
            a("Carmen Rojas", "88.7"), a("Rosa Torres", "85"));

    @Test
    @DisplayName("rotan A, B, C una por semana y vuelven a empezar")
    void rotacion() {
        SemanaDelRanking primera = semanaCon(Plantilla.A);

        assertThat(TextoDelPodio.plantillaDe(siguiente(primera, 1))).isEqualTo(Plantilla.B);
        assertThat(TextoDelPodio.plantillaDe(siguiente(primera, 2))).isEqualTo(Plantilla.C);
        assertThat(TextoDelPodio.plantillaDe(siguiente(primera, 3))).isEqualTo(Plantilla.A);
    }

    @Test
    @DisplayName("plantilla A con tres distintos: el texto aprobado, tal cual")
    void plantillaA() {
        assertThat(TextoDelPodio.para(TRES, semanaCon(Plantilla.A))).isEqualTo("""
                🏆 ¡Cerramos la semana y este es nuestro podio!
                🥇 Felicidades, Liz M., ¡primer puesto! Tu constancia inspira a toda la comunidad.
                🥈 Jorge P. y 🥉 Carmen R., ¡qué gran semana! Se nota cómo avanzan en su transformación 👏
                🔥 Y tú, aprendiz, ¿qué esperas para superar a tus compañeros? Esta semana empieza de cero: cada hábito cuenta 💪""");
    }

    @Test
    @DisplayName("plantilla B con tres distintos: el texto aprobado, tal cual")
    void plantillaB() {
        assertThat(TextoDelPodio.para(TRES, semanaCon(Plantilla.B))).isEqualTo("""
                ✨ Ranking general de la semana ✨
                👑 Liz M. se queda con el 1.er puesto, ¡felicidades!
                🌟 Jorge P. (2.º) y Carmen R. (3.º): su compromiso se nota día a día.
                🚀 Aprendiz, la próxima foto puede llevar tu nombre. ¿Qué esperas para superar a tus compañeros? 🔥""");
    }

    @Test
    @DisplayName("plantilla C con tres distintos: el texto aprobado, tal cual")
    void plantillaC() {
        assertThat(TextoDelPodio.para(TRES, semanaCon(Plantilla.C))).isEqualTo("""
                🌅 ¡Feliz lunes, comunidad Renaser!
                🥇 Liz M., 🥈 Jorge P. y 🥉 Carmen R. lideraron la semana. ¡Felicidades por su constancia! 🙌
                💛 A cada aprendiz: no importa dónde estés hoy, lo que cuenta es el paso que das ahora.
                💪 ¿Qué esperas para superar a tus compañeros? ¡Nos vemos en el podio! 🏆""");
    }

    @Test
    @DisplayName("empate en el primero: «comparten el primer puesto», y el que sigue es 3.º con bronce")
    void empateEnElPrimero() {
        PodioDeLaSemana podio = podio(a("Ana Quispe", "90"), a("Beto Ruiz", "90"), a("Carla Díaz", "70"));

        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.A)))[1])
                .isEqualTo("🥇 Felicidades, Ana Q. y Beto R., ¡comparten el primer puesto! Su constancia inspira a toda la comunidad.");
        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.A)))[2])
                .isEqualTo("🥉 Carla D., ¡qué gran semana! Se nota cómo avanzas en tu transformación 👏");
        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.B)))[1])
                .isEqualTo("👑 Ana Q. y Beto R. comparten el 1.er puesto, ¡felicidades!");
        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.B)))[2])
                .isEqualTo("🌟 Carla D. (3.º): tu compromiso se nota día a día.");
        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.C)))[1])
                .isEqualTo("🥇 Ana Q., 🥇 Beto R. y 🥉 Carla D. lideraron la semana. ¡Felicidades por su constancia! 🙌");
    }

    @Test
    @DisplayName("con dos personas no queda ningún hueco")
    void dos() {
        PodioDeLaSemana podio = podio(a("Ana Quispe", "90"), a("Beto Ruiz", "80"));

        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.A)))[2])
                .isEqualTo("🥈 Beto R., ¡qué gran semana! Se nota cómo avanzas en tu transformación 👏");
        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.B)))[2])
                .isEqualTo("🌟 Beto R. (2.º): tu compromiso se nota día a día.");
        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.C)))[1])
                .isEqualTo("🥇 Ana Q. y 🥈 Beto R. lideraron la semana. ¡Felicidades por su constancia! 🙌");
    }

    @Test
    @DisplayName("con una sola persona: sin la línea del 2.º y el 3.º, y en singular")
    void una() {
        PodioDeLaSemana podio = podio(a("Ana Quispe", "40"));

        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.A)))).hasSize(3)
                .noneMatch(l -> l.contains("{") || l.contains("🥈") || l.contains("🥉"));
        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.B)))).hasSize(3);
        assertThat(lineas(TextoDelPodio.para(podio, semanaCon(Plantilla.C)))[1])
                .isEqualTo("🥇 Ana Q. lideró la semana. ¡Felicidades por tu constancia! 🙌");
    }

    @Test
    @DisplayName("un podio vacío no tiene texto")
    void vacio() {
        assertThatThrownBy(() -> TextoDelPodio.para(podio(a("Ana", "0")), semanaCon(Plantilla.A)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** La primera semana desde el 28 de septiembre de 2026 a la que le toca {@code plantilla}. */
    private static SemanaDelRanking semanaCon(Plantilla plantilla) {
        SemanaDelRanking semana = new SemanaDelRanking(LocalDate.of(2026, 9, 28));
        while (TextoDelPodio.plantillaDe(semana) != plantilla) {
            semana = siguiente(semana, 1);
        }
        return semana;
    }

    private static String[] lineas(String texto) {
        return texto.split("\n");
    }

    private static PodioDeLaSemana podio(Aprendiz... aprendices) {
        return PodioDeLaSemana.de(Arrays.asList(aprendices));
    }

    private static Aprendiz a(String nombre, String puntaje) {
        return new Aprendiz(nombre, new BigDecimal(puntaje));
    }

    private static SemanaDelRanking siguiente(SemanaDelRanking semana, int semanas) {
        return new SemanaDelRanking(semana.lunes().plusWeeks(semanas));
    }
}
