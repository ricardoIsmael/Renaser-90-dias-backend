package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /mentor/groups/{g}/learners/{u}/semana} de punta a punta (spec 055): la semana de sábado a viernes del aprendiz con
 * el pilar de cada hábito, el Día 1 que limita el mes y los rechazos con su código.
 */
class SemanaDelAprendizIT extends EscenarioDelMentorIT {

    @Test
    @DisplayName("spec 055 FR-011 FR-013: sin fecha trae la semana en curso de sábado a viernes, con el pilar de cada hábito y el Día 1")
    void laSemanaEnCursoTraeElPilarYElDiaUno() throws Exception {
        habitoDelDia(ana, "2026-11-17", "Caminar", "CUERPO", "COMPLETADO");
        habitoDelDia(ana, "2026-11-17", "Meditar", "CONSCIENCIA", "FALLIDO");

        HttpResponse<String> respuesta = get(delMentor(fenix, ana, "semana"), mentora);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        JsonNode semana = cuerpo(respuesta);
        assertThat(semana.get("aprendizId").asText()).isEqualTo(ana.toString());
        assertThat(semana.get("nombre").asText()).isEqualTo("Ana Pérez");
        assertThat(semana.get("zona").asText()).isEqualTo("America/Lima");
        assertThat(semana.get("desde").asText()).isEqualTo("2026-11-14");
        assertThat(semana.get("hasta").asText()).isEqualTo("2026-11-20");
        assertThat(semana.get("diaUno").asText()).isEqualTo("2026-10-05");
        assertThat(semana.get("primeraSemana").asText()).isEqualTo("2026-10-03");
        assertThat(semana.get("semanaEnCurso").asBoolean()).isTrue();
        assertThat(semana.get("cerrada").asBoolean()).isFalse();
        assertThat(semana.get("dias")).hasSize(7);
        JsonNode martes = semana.get("dias").get(3);
        assertThat(martes.get("fecha").asText()).isEqualTo("2026-11-17");
        assertThat(martes.get("obligaciones")).extracting(o -> o.get("titulo").asText().split(" ")[0] + ":" + o.get("pilar").asText())
                .containsExactlyInAnyOrder("Caminar:CUERPO", "Meditar:CONSCIENCIA");
    }

    @Test
    @DisplayName("spec 055 FR-011: con el viernes de una semana ya cerrada trae esa semana, cerrada")
    void unaSemanaCerrada() throws Exception {
        HttpResponse<String> respuesta = get(delMentor(fenix, ana, "semana") + "?semanaHasta=2026-11-13", mentora);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(cuerpo(respuesta).get("desde").asText()).isEqualTo("2026-11-07");
        assertThat(cuerpo(respuesta).get("cerrada").asBoolean()).isTrue();
        assertThat(cuerpo(respuesta).get("semanaEnCurso").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("spec 055 FR-015: la primera semana (la del sábado 3 de octubre) se puede pedir")
    void laPrimeraSemanaSePuedePedir() throws Exception {
        HttpResponse<String> respuesta = get(delMentor(fenix, ana, "semana") + "?semanaHasta=2026-10-09", mentora);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(cuerpo(respuesta).get("desde").asText()).isEqualTo("2026-10-03");
    }

    @Test
    @DisplayName("spec 055 FR-011: una fecha que no es viernes es 400 SEMANA_INVALIDA")
    void unaFechaQueNoEsViernes() throws Exception {
        HttpResponse<String> respuesta = get(delMentor(fenix, ana, "semana") + "?semanaHasta=2026-11-17", mentora);

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(codigo(respuesta)).isEqualTo("SEMANA_INVALIDA");
        assertThat(cuerpo(respuesta).get("message").asText()).isNotBlank();
    }

    @Test
    @DisplayName("spec 055 FR-015 FR-017: antes de la semana del Día 1, o una semana futura, es 400 FUERA_DEL_PROGRAMA")
    void fueraDelPrograma() throws Exception {
        for (String viernes : new String[] {"2026-10-02", "2026-11-27"}) {
            HttpResponse<String> respuesta = get(delMentor(fenix, ana, "semana") + "?semanaHasta=" + viernes, mentora);

            assertThat(respuesta.statusCode()).as(viernes).isEqualTo(400);
            assertThat(codigo(respuesta)).as(viernes).isEqualTo("FUERA_DEL_PROGRAMA");
        }
    }

    @Test
    @DisplayName("spec 055 FR-017 FR-028: otro mentor, el que ya no acompaña, un aprendiz ajeno y el propio aprendiz reciben 403; la cuenta suspendida también")
    void soloLoVeQuienAcompanaHoy() throws Exception {
        UUID luis = usuario("APRENDIZ", "Luis Soto", "ACTIVO");
        programa(luis, "2026-10-05");
        asignar(aurora, luis, "APRENDIZ");

        assertThat(get(delMentor(fenix, ana, "semana"), otraMentora).statusCode()).as("mentor de otro grupo").isEqualTo(403);
        assertThat(codigo(get(delMentor(fenix, ana, "semana"), otraMentora))).isEqualTo("NO_ACOMPANA");
        assertThat(get(delMentor(fenix, luis, "semana"), mentora).statusCode()).as("aprendiz de otro grupo").isEqualTo(403);
        assertThat(get(delMentor(fenix, ana, "semana"), ana).statusCode()).as("el propio aprendiz").isEqualTo(403);
        assertThat(get(delMentor(fenix, ana, "semana"), lider).statusCode()).as("el líder no es su mentor").isEqualTo(403);

        terminarAsignacion(fenix, mentora);
        assertThat(get(delMentor(fenix, ana, "semana"), mentora).statusCode()).as("mentor rotado").isEqualTo(403);
    }

    @Test
    @DisplayName("spec 055 FR-017 FR-028: una cuenta suspendida con la sesión viva recibe 403")
    void laCuentaSuspendida() throws Exception {
        String token = sesionDe(mentora);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", mentora);

        assertThat(get(delMentor(fenix, ana, "semana"), mentora).statusCode()).isEqualTo(403);
        assertThat(token).isNotBlank();
    }
}
