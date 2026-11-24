package com.renaser.os.chat.domain.model.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Aprendiz;
import com.renaser.os.chat.domain.model.ranking.TextoDelPodio.Plantilla;
import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** D-367: los cuatro rankings del lunes: sus unidades, sus ids, su rotación y, sobre todo, que cada texto hable de lo suyo. */
class CuatroRankingsDelPodioTest {

    private static final SemanaDelRanking SEMANA = new SemanaDelRanking(LocalDate.of(2026, 11, 16));
    private static final List<TipoDeRankingSemanal> NUEVOS = List.of(TipoDeRankingSemanal.COHERENCIA,
            TipoDeRankingSemanal.KILOMETROS, TipoDeRankingSemanal.GIMNASIO);

    @Test
    @DisplayName("el orden de publicación es General, Coherencia, Kilómetros y Gimnasio; la Liga no está")
    void orden() {
        assertThat(TipoDeRankingSemanal.values()).containsExactly(TipoDeRankingSemanal.GENERAL, TipoDeRankingSemanal.COHERENCIA,
                TipoDeRankingSemanal.KILOMETROS, TipoDeRankingSemanal.GIMNASIO);
        assertThat(TipoDeRankingSemanal.deCodigo("Kilometros")).isEqualTo(TipoDeRankingSemanal.KILOMETROS);
        assertThat(TipoDeRankingSemanal.deCodigo("liga")).isNull();
        assertThat(TipoDeRankingSemanal.deCodigo(null)).isNull();
    }

    @Test
    @DisplayName("cada valor con su unidad: pts y % con un decimal y coma, km con un decimal y los días enteros, en singular y plural")
    void unidades() {
        assertThat(TipoDeRankingSemanal.GENERAL.conUnidad(new BigDecimal("96.44"))).isEqualTo("96,4 pts");
        assertThat(TipoDeRankingSemanal.COHERENCIA.conUnidad(new BigDecimal("87.5"))).isEqualTo("87,5 %");
        assertThat(TipoDeRankingSemanal.KILOMETROS.conUnidad(new BigDecimal("42.50"))).isEqualTo("42,5 km");
        assertThat(TipoDeRankingSemanal.KILOMETROS.conUnidad(new BigDecimal("3"))).isEqualTo("3,0 km");
        assertThat(TipoDeRankingSemanal.GIMNASIO.conUnidad(new BigDecimal("1"))).isEqualTo("1 día");
        assertThat(TipoDeRankingSemanal.GIMNASIO.conUnidad(new BigDecimal("5"))).isEqualTo("5 días");
    }

    @Test
    @DisplayName("el podio de cada ranking sigue las reglas del General: top 5, sin ceros y los empates comparten puesto")
    void mismasReglas() {
        for (TipoDeRankingSemanal tipo : TipoDeRankingSemanal.values()) {
            PodioDeLaSemana podio = PodioDeLaSemana.de(tipo, List.of(a("Ana Q", "9"), a("Beto R", "9"), a("Carla D", "7"), a("Dina S", "5"),
                    a("Eva T", "4"), a("Fede U", "3"), a("Gina V", "0")));

            assertThat(podio.tipo()).isEqualTo(tipo);
            assertThat(podio.todos()).hasSize(5);
            assertThat(podio.todos()).extracting(PodioDeLaSemana.Puesto::lugar).containsExactly(1, 1, 3, 4, 5);
        }
        assertThat(PodioDeLaSemana.de(TipoDeRankingSemanal.GIMNASIO, List.of(a("Ana Q", "0"))).estaVacio()).as("ranking sin nadie con valor").isTrue();
        assertThat(PodioDeLaSemana.de(List.of(a("Ana Q", "1"))).tipo()).as("sin tipo es el General de siempre").isEqualTo(TipoDeRankingSemanal.GENERAL);
    }

    @Test
    @DisplayName("el General conserva su id y su ruta (la semana ya publicada no se repite); los otros tres tienen los suyos, distintos entre sí")
    void ids() {
        for (Formacion formacion : Formacion.values()) {
            assertThat(SEMANA.idDelTexto(formacion, TipoDeRankingSemanal.GENERAL)).isEqualTo(SEMANA.idDelTexto(formacion));
            assertThat(SEMANA.idDeLaImagen(formacion, TipoDeRankingSemanal.GENERAL)).isEqualTo(SEMANA.idDeLaImagen(formacion));
            assertThat(SEMANA.rutaDeLaImagen(formacion, TipoDeRankingSemanal.GENERAL)).isEqualTo(SEMANA.rutaDeLaImagen(formacion));
            Set<Object> ids = new HashSet<>();
            Set<String> rutas = new HashSet<>();
            for (TipoDeRankingSemanal tipo : TipoDeRankingSemanal.values()) {
                ids.add(SEMANA.idDelTexto(formacion, tipo));
                ids.add(SEMANA.idDeLaImagen(formacion, tipo));
                rutas.add(SEMANA.rutaDeLaImagen(formacion, tipo));
            }
            assertThat(ids).as("8 ids distintos por formación").hasSize(8);
            assertThat(rutas).hasSize(4);
        }
        assertThat(SEMANA.rutaDeLaImagen(Formacion.ESCUELA, TipoDeRankingSemanal.GENERAL)).isEqualTo("ranking-semanal/2026-11-16-v1.jpg");
        assertThat(SEMANA.rutaDeLaImagen(Formacion.ESCUELA, TipoDeRankingSemanal.KILOMETROS)).isEqualTo("ranking-semanal/2026-11-16-kilometros-v1.jpg");
        assertThat(SEMANA.rutaDeLaImagen(Formacion.DIPLOMADO, TipoDeRankingSemanal.GIMNASIO)).isEqualTo("ranking-semanal/diplomado/2026-11-16-gimnasio-v1.jpg");
        assertThat(SEMANA.idDelTexto(Formacion.DIPLOMADO, TipoDeRankingSemanal.COHERENCIA)).isNotEqualTo(SEMANA.idDelTexto(Formacion.ESCUELA, TipoDeRankingSemanal.COHERENCIA));
    }

    @Test
    @DisplayName("la plantilla de cada ranking rota por semana y va desfasada: el General sigue igual y los cuatro de un lunes no repiten letra")
    void rotacion() {
        assertThat(TextoDelPodio.plantillaDe(SEMANA)).isEqualTo(TextoDelPodio.plantillaDe(SEMANA, TipoDeRankingSemanal.GENERAL));
        for (int semanas = 0; semanas < 6; semanas++) {
            SemanaDelRanking semana = new SemanaDelRanking(SEMANA.lunes().plusWeeks(semanas));
            Plantilla coherencia = TextoDelPodio.plantillaDe(semana, TipoDeRankingSemanal.COHERENCIA);
            Plantilla kilometros = TextoDelPodio.plantillaDe(semana, TipoDeRankingSemanal.KILOMETROS);
            Plantilla gimnasio = TextoDelPodio.plantillaDe(semana, TipoDeRankingSemanal.GIMNASIO);
            assertThat(new HashSet<>(List.of(TextoDelPodio.plantillaDe(semana), coherencia, kilometros))).hasSize(3);
            assertThat(new HashSet<>(List.of(coherencia, kilometros, gimnasio))).hasSizeGreaterThanOrEqualTo(2);
            Plantilla siguiente = TextoDelPodio.plantillaDe(new SemanaDelRanking(semana.lunes().plusWeeks(1)), TipoDeRankingSemanal.KILOMETROS);
            assertThat(siguiente).as("rota cada semana").isNotEqualTo(kilometros);
        }
    }

    @Test
    @DisplayName("cada ranking habla de lo suyo en las tres plantillas y no usa frases de otro (no «cada hábito cuenta» en Kilómetros)")
    void cadaTextoHablaDeLoSuyo() {
        for (Plantilla plantilla : Plantilla.values()) {
            String coherencia = texto(TipoDeRankingSemanal.COHERENCIA, plantilla);
            String kilometros = texto(TipoDeRankingSemanal.KILOMETROS, plantilla);
            String gimnasio = texto(TipoDeRankingSemanal.GIMNASIO, plantilla);

            assertThat(coherencia).containsIgnoringCase("coherencia").containsAnyOf("planific", "objetivo");
            assertThat(kilometros).containsIgnoringCase("kilómetro").containsAnyOf("recorr", "pasos", "camin");
            assertThat(gimnasio).containsIgnoringCase("gimnasio").containsAnyOf("entren", "entrenamiento");
            for (String texto : List.of(coherencia, kilometros, gimnasio)) {
                assertThat(texto).doesNotContain("cada hábito cuenta").doesNotContain("constancia");
                assertThat(texto).as("nada de voseo").doesNotContainPattern("\\b(vos|tenés|podés|querés|sos|sabés|hacé|salí)\\b");
                assertThat(texto).contains("Liz M.");
            }
            assertThat(new HashSet<>(List.of(coherencia, kilometros, gimnasio))).as("textos distintos").hasSize(3);
        }
    }

    @Test
    @DisplayName("cada ranking tiene tres textos distintos entre sí, con arranque y cierre propios")
    void tresPlantillasDistintas() {
        for (TipoDeRankingSemanal tipo : NUEVOS) {
            Set<String> aperturas = new HashSet<>();
            Set<String> cierres = new HashSet<>();
            for (Plantilla plantilla : Plantilla.values()) {
                String[] lineas = texto(tipo, plantilla).split("\n");
                aperturas.add(lineas[0]);
                cierres.add(lineas[lineas.length - 1]);
            }
            assertThat(aperturas).as("%s: arranques distintos", tipo).hasSize(3);
            assertThat(cierres).as("%s: cierres distintos", tipo).hasSize(3);
        }
        for (Plantilla plantilla : Plantilla.values()) {
            Set<String> aperturas = new HashSet<>();
            Set<String> cierres = new HashSet<>();
            for (TipoDeRankingSemanal tipo : TipoDeRankingSemanal.values()) {
                String[] lineas = texto(tipo, plantilla).split("\n");
                aperturas.add(lineas[0]);
                cierres.add(lineas[lineas.length - 1]);
            }
            assertThat(aperturas).as("misma plantilla, cuatro arranques distintos").hasSize(4);
            assertThat(cierres).as("misma plantilla, cuatro cierres distintos").hasSize(4);
        }
    }

    @Test
    @DisplayName("la felicitación al primero lleva su valor de la semana con su unidad (km con un decimal, días enteros, % con un decimal)")
    void llevaElValor() {
        assertThat(texto(TipoDeRankingSemanal.KILOMETROS, Plantilla.A)).contains("42,5 km");
        assertThat(texto(TipoDeRankingSemanal.KILOMETROS, Plantilla.B)).contains("42,5 km");
        assertThat(texto(TipoDeRankingSemanal.GIMNASIO, Plantilla.A)).contains("6 días");
        assertThat(texto(TipoDeRankingSemanal.GIMNASIO, Plantilla.B)).contains("6 días");
        assertThat(texto(TipoDeRankingSemanal.COHERENCIA, Plantilla.A)).contains("93,8 %");
        assertThat(texto(TipoDeRankingSemanal.COHERENCIA, Plantilla.B)).contains("93,8 %");
        PodioDeLaSemana uno = PodioDeLaSemana.de(TipoDeRankingSemanal.GIMNASIO, List.of(a("Ana Quispe", "1")));
        assertThat(TextoDelPodio.para(uno, semanaCon(TipoDeRankingSemanal.GIMNASIO, Plantilla.A))).contains("Entrenaste 1 día esta semana");
    }

    @Test
    @DisplayName("empates y singular/plural: dos primeros «comparten», una sola persona va en singular y sin línea de resto, sin huecos")
    void empatesYPlural() {
        for (TipoDeRankingSemanal tipo : NUEVOS) {
            for (Plantilla plantilla : Plantilla.values()) {
                SemanaDelRanking semana = semanaCon(tipo, plantilla);
                String empate = TextoDelPodio.para(PodioDeLaSemana.de(tipo, List.of(a("Ana Quispe", "9"), a("Beto Ruiz", "9"),
                        a("Carla Díaz", "5"))), semana);
                assertThat(empate).contains("Ana Q.").contains("Beto R.");
                if (plantilla != Plantilla.C) {
                    assertThat(empate).contains("Ana Q. y Beto R.").contains("comparten");
                } else {
                    assertThat(empate).contains("🥇 Ana Q., 🥇 Beto R. y 🥉 Carla D.");
                }
                String sola = TextoDelPodio.para(PodioDeLaSemana.de(tipo, List.of(a("Ana Quispe", "9"))), semana);
                assertThat(sola).contains("Ana Q.").doesNotContain("comparten").doesNotContain("null").doesNotContain("%1$s").doesNotContain("%2$s");
                assertThat(sola.split("\n")).as("%s %s: sin línea vacía", tipo, plantilla).noneMatch(String::isBlank);
            }
        }
    }

    @Test
    @DisplayName("el texto del General no cambió: sigue siendo el aprobado")
    void generalIntacto() {
        PodioDeLaSemana podio = PodioDeLaSemana.de(List.of(a("Liz Mendoza", "96.4")));
        assertThat(TextoDelPodio.para(podio, semanaCon(TipoDeRankingSemanal.GENERAL, Plantilla.A)))
                .startsWith("🏆 ¡Cerramos la semana y este es nuestro podio!").contains("cada hábito cuenta");
    }

    private static String texto(TipoDeRankingSemanal tipo, Plantilla plantilla) {
        return TextoDelPodio.para(PodioDeLaSemana.de(tipo, filas(tipo)), semanaCon(tipo, plantilla));
    }

    private static List<Aprendiz> filas(TipoDeRankingSemanal tipo) {
        return switch (tipo) {
            case COHERENCIA -> List.of(a("Liz Mendoza", "93.8"), a("Jorge Pérez", "90"), a("Rosa Torres", "80"));
            case KILOMETROS -> List.of(a("Liz Mendoza", "42.5"), a("Jorge Pérez", "30"), a("Rosa Torres", "20"));
            case GIMNASIO -> List.of(a("Liz Mendoza", "6"), a("Jorge Pérez", "5"), a("Rosa Torres", "3"));
            case GENERAL -> List.of(a("Liz Mendoza", "96.4"), a("Jorge Pérez", "92.1"), a("Rosa Torres", "88"));
        };
    }

    private static SemanaDelRanking semanaCon(TipoDeRankingSemanal tipo, Plantilla plantilla) {
        SemanaDelRanking semana = SEMANA;
        while (TextoDelPodio.plantillaDe(semana, tipo) != plantilla) {
            semana = new SemanaDelRanking(semana.lunes().plusWeeks(1));
        }
        return semana;
    }

    private static Aprendiz a(String nombre, String valor) {
        return new Aprendiz(nombre, new BigDecimal(valor));
    }
}
