package com.renaser.os.notifications.domain.model.semaforo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los textos de los avisos del sábado, caso por caso (D-168). Lo que se cuida: que cada caso diga lo
 * suyo, y que ninguno lleve cifras, colores ni sus palabras, ni quede vacío o con huecos, porque el
 * mismo texto sale por push a la pantalla bloqueada.
 */
class RedaccionDelSemaforoTest {

    private static final List<String> PROHIBIDAS =
            List.of("verde", "amarillo", "rojo", "atención", "problemas", "al día", "sin datos", "%", "null", "{", "}");

    // ── la persona ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("con algo programado: la invita a mirar cómo le fue, sin decir cómo le fue")
    void personaConRegistros() {
        AvisoRedactado aviso = RedaccionDelSemaforo.paraLaPersona(true);

        assertThat(aviso.caso()).isEqualTo(CasoDelAviso.CON_REGISTROS);
        assertThat(aviso.titulo()).isEqualTo("Tu semana ya cerró");
        assertThat(aviso.cuerpo()).isEqualTo("Mira cómo te fue en tu semáforo de la semana.");
    }

    @Test
    @DisplayName("sin nada programado: la invita a planificar, sin nombrar un color")
    void personaSinRegistros() {
        AvisoRedactado aviso = RedaccionDelSemaforo.paraLaPersona(false);

        assertThat(aviso.caso()).isEqualTo(CasoDelAviso.SIN_REGISTROS);
        assertThat(aviso.cuerpo()).startsWith("Esta semana no tuviste hábitos ni objetivos programados.");
    }

    // ── el mentor ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("con aprendices en amarillo o rojo: le pide su apoyo, nombrando solo al grupo")
    void mentorNecesitanApoyo() {
        AvisoRedactado aviso = RedaccionDelSemaforo.paraElMentor("Grupo Fénix", new ConteoDeLaSemana(5, 2, 1, 0));

        assertThat(aviso.caso()).isEqualTo(CasoDelAviso.NECESITAN_APOYO);
        assertThat(aviso.titulo()).isEqualTo("Tu grupo cerró la semana");
        assertThat(aviso.cuerpo())
                .isEqualTo("En Grupo Fénix hay aprendices que necesitan tu apoyo. Mira el semáforo del grupo.");
    }

    @Test
    @DisplayName("un solo aprendiz en rojo ya es un grupo que necesita apoyo")
    void unoEnRojoAlcanza() {
        assertThat(RedaccionDelSemaforo.paraElMentor("Fénix", new ConteoDeLaSemana(9, 0, 1, 3)).caso())
                .isEqualTo(CasoDelAviso.NECESITAN_APOYO);
    }

    @Test
    @DisplayName("todos con registros y ninguno en amarillo ni rojo: nadie necesita apoyo extra")
    void mentorSinAlertas() {
        AvisoRedactado aviso = RedaccionDelSemaforo.paraElMentor("Fénix", new ConteoDeLaSemana(4, 0, 0, 0));

        assertThat(aviso.caso()).isEqualTo(CasoDelAviso.SIN_ALERTAS);
        assertThat(aviso.cuerpo()).isEqualTo("En Fénix, nadie necesita apoyo extra esta semana. ¡Buen acompañamiento!");
    }

    @Test
    @DisplayName("nadie con registros: lo dice sin inventar un color")
    void mentorSinRegistros() {
        AvisoRedactado aviso = RedaccionDelSemaforo.paraElMentor("Fénix", new ConteoDeLaSemana(0, 0, 0, 3));

        assertThat(aviso.caso()).isEqualTo(CasoDelAviso.SIN_REGISTROS);
        assertThat(aviso.cuerpo()).isEqualTo("En Fénix, nadie tuvo registros en el semáforo esta semana.");
    }

    @Test
    @DisplayName("verdes y sin registros mezclados: no afirma que nadie necesita apoyo; texto neutro")
    void mentorMezclaNeutra() {
        AvisoRedactado aviso = RedaccionDelSemaforo.paraElMentor("Fénix", new ConteoDeLaSemana(2, 0, 0, 1));

        assertThat(aviso.caso()).isEqualTo(CasoDelAviso.NEUTRO);
        assertThat(aviso.cuerpo()).isEqualTo("El semáforo de Fénix ya está listo.");
    }

    @Test
    @DisplayName("un grupo sin nombre se dice «tu grupo», sin dejar un hueco")
    void grupoSinNombre() {
        for (String nombre : new String[]{null, "", "   "}) {
            assertThat(RedaccionDelSemaforo.paraElMentor(nombre, new ConteoDeLaSemana(1, 1, 0, 0)).cuerpo())
                    .isEqualTo("En tu grupo hay aprendices que necesitan tu apoyo. Mira el semáforo del grupo.");
        }
    }

    @Test
    @DisplayName("un nombre de grupo larguísimo se corta con puntos suspensivos")
    void nombreLargo() {
        String nombre = "Grupo " + "a".repeat(100);

        String cuerpo = RedaccionDelSemaforo.paraElMentor(nombre, new ConteoDeLaSemana(1, 0, 0, 0)).cuerpo();

        assertThat(cuerpo).contains("…").doesNotContain("a".repeat(60));
    }

    @Test
    @DisplayName("un conteo que no cierra (negativo, vacío o ausente) no afirma nada: texto neutro")
    void conteoQueNoCierra() {
        for (ConteoDeLaSemana conteo : new ConteoDeLaSemana[]{
                new ConteoDeLaSemana(-1, 0, 0, 0), new ConteoDeLaSemana(0, 0, 0, 0), null}) {
            AvisoRedactado aviso = RedaccionDelSemaforo.paraElMentor("Fénix", conteo);

            assertThat(aviso.caso()).isEqualTo(CasoDelAviso.NEUTRO);
            assertThat(aviso.cuerpo()).isEqualTo("El semáforo de Fénix ya está listo.");
        }
    }

    // ── líder, administración y alquimia ────────────────────────────────────

    @Test
    @DisplayName("la conducción recibe un texto por caso, sin grupos ni personas")
    void conduccionPorCaso() {
        assertThat(RedaccionDelSemaforo.paraLaConduccion(new ConteoDeLaSemana(40, 12, 8, 2)).cuerpo())
                .isEqualTo("Hay grupos con aprendices que necesitan apoyo. Mira el semáforo por grupos.");
        assertThat(RedaccionDelSemaforo.paraLaConduccion(new ConteoDeLaSemana(30, 0, 0, 0)).cuerpo())
                .isEqualTo("Ningún grupo tiene aprendices que necesiten apoyo extra esta semana.");
        assertThat(RedaccionDelSemaforo.paraLaConduccion(new ConteoDeLaSemana(0, 0, 0, 5)).cuerpo())
                .isEqualTo("Ningún grupo tuvo registros en el semáforo esta semana.");
        assertThat(RedaccionDelSemaforo.paraLaConduccion(null).cuerpo())
                .isEqualTo("El semáforo de los grupos ya está listo.");
        assertThat(RedaccionDelSemaforo.paraLaConduccion(new ConteoDeLaSemana(1, 1, 0, 0)).titulo())
                .isEqualTo("Semana cerrada");
    }

    // ── las reglas duras, en todos los casos ────────────────────────────────

    @Test
    @DisplayName("ningún texto de ningún caso lleva cifras, colores ni sus palabras, ni queda vacío")
    void reglasDurasEnTodosLosCasos() {
        List<ConteoDeLaSemana> conteos = List.of(new ConteoDeLaSemana(5, 2, 1, 0), new ConteoDeLaSemana(4, 0, 0, 0),
                new ConteoDeLaSemana(0, 0, 0, 3), new ConteoDeLaSemana(2, 0, 0, 1), new ConteoDeLaSemana(-1, 0, 0, 0));
        List<AvisoRedactado> avisos = new ArrayList<>(List.of(
                RedaccionDelSemaforo.paraLaPersona(true), RedaccionDelSemaforo.paraLaPersona(false)));
        conteos.forEach(conteo -> {
            avisos.add(RedaccionDelSemaforo.paraElMentor("Grupo Fénix", conteo));
            avisos.add(RedaccionDelSemaforo.paraLaConduccion(conteo));
        });

        assertThat(avisos).extracting(AvisoRedactado::caso).containsAll(List.of(CasoDelAviso.values()));
        for (AvisoRedactado aviso : avisos) {
            for (String texto : new String[]{aviso.titulo(), aviso.cuerpo()}) {
                assertThat(texto).isNotBlank().doesNotContainPattern("\\d");
                assertThat(texto.toLowerCase()).doesNotContain(PROHIBIDAS);
            }
        }
    }
}
