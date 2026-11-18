package com.renaser.os.leadership;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /leadership/people/{p}/semana} de punta a punta (spec 056): la semana de sábado a viernes de una persona con el pilar de
 * cada hábito, en la misma forma que la del mentor (spec 055), para la ficha del líder.
 */
class SemanaDeLaPersonaIT extends EscenarioDelLiderIT {

    @Test
    @DisplayName("spec 056 FR-014: sin fecha trae la semana en curso de sábado a viernes con el Día 1, la primera semana y el pilar de cada hábito")
    void laSemanaEnCurso() throws Exception {
        habitoDelDia(ana, "2026-11-17", "Caminar", "CUERPO", "COMPLETADO");
        habitoDelDia(ana, "2026-11-17", "Meditar", "CONSCIENCIA", "FALLIDO");

        HttpResponse<String> respuesta = get(delLider(ana, "semana"), lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode semana = cuerpo(respuesta);
        assertThat(semana.get("aprendizId").asText()).isEqualTo(ana.toString());
        assertThat(semana.get("nombre").asText()).isEqualTo("Ana Pérez");
        assertThat(semana.get("zona").asText()).isEqualTo("America/Lima");
        assertThat(semana.get("desde").asText()).isEqualTo("2026-11-14");
        assertThat(semana.get("hasta").asText()).isEqualTo("2026-11-20");
        assertThat(semana.get("diaUno").asText()).isEqualTo(DIA_UNO);
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
    @DisplayName("spec 056 FR-014: es el mismo cuerpo que ve el mentor de la persona (spec 055), con y sin viernes pedido")
    void lamismaFormaQueLaDelMentor() throws Exception {
        habitoDelDia(ana, "2026-11-12", "Caminar", "CUERPO", "COMPLETADO");

        for (String consulta : List.of("", "?semanaHasta=2026-11-13")) {
            HttpResponse<String> delLider = get(delLider(ana, "semana") + consulta, lider);
            HttpResponse<String> delMentor = get(delMentor(fenix, ana, "semana") + consulta, mentora);

            assertThat(delLider.statusCode()).as(consulta).isEqualTo(200);
            assertThat(cuerpo(delLider)).as(consulta).isEqualTo(cuerpo(delMentor));
        }
        assertThat(cuerpo(get(delLider(ana, "semana") + "?semanaHasta=2026-11-13", lider)).get("cerrada").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("spec 056 FR-014: la primera semana (la del sábado 3 de octubre) se puede pedir; quien no está en un grupo también tiene su semana")
    void laPrimeraSemanaYQuienNoTieneGrupo() throws Exception {
        HttpResponse<String> primera = get(delLider(ana, "semana") + "?semanaHasta=2026-10-09", lider);
        HttpResponse<String> sinGrupo = get(delLider(beto, "semana"), lider);

        assertThat(primera.statusCode()).as(primera.body()).isEqualTo(200);
        assertThat(cuerpo(primera).get("desde").asText()).isEqualTo("2026-10-03");
        assertThat(sinGrupo.statusCode()).as(sinGrupo.body()).isEqualTo(200);
        assertThat(cuerpo(sinGrupo).get("aprendizId").asText()).isEqualTo(beto.toString());
    }

    @Test
    @DisplayName("spec 056 FR-014: una fecha que no es viernes es 400 SEMANA_INVALIDA; antes del Día 1 o una semana futura, 400 FUERA_DEL_PROGRAMA")
    void fechasInvalidas() throws Exception {
        HttpResponse<String> noViernes = get(delLider(ana, "semana") + "?semanaHasta=2026-11-17", lider);

        assertThat(noViernes.statusCode()).isEqualTo(400);
        assertThat(codigo(noViernes)).isEqualTo("SEMANA_INVALIDA");
        for (String viernes : new String[] {"2026-10-02", "2026-11-27"}) {
            HttpResponse<String> respuesta = get(delLider(ana, "semana") + "?semanaHasta=" + viernes, lider);

            assertThat(respuesta.statusCode()).as(viernes).isEqualTo(400);
            assertThat(codigo(respuesta)).as(viernes).isEqualTo("FUERA_DEL_PROGRAMA");
        }
    }

    @Test
    @DisplayName("spec 056 FR-021: la semana de un mentor que lleva su programa se abre sin color de semáforo y con sus hábitos")
    void laSemanaDeUnMentorConPrograma() throws Exception {
        habitoDelDia(mentorConPrograma, "2026-11-17", "Leer", "CONSCIENCIA", "COMPLETADO");

        HttpResponse<String> respuesta = get(delLider(mentorConPrograma, "semana"), lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        JsonNode semana = cuerpo(respuesta);
        assertThat(semana.get("aprendizId").asText()).isEqualTo(mentorConPrograma.toString());
        assertThat(semana.get("color").asText()).as("el mentor no cuenta en el semáforo de ningún grupo").isEqualTo("NO_SE_MIDE");
        assertThat(semana.get("dias").get(3).get("obligaciones")).hasSize(1);
    }

    @Test
    @DisplayName("spec 056 FR-022: un mentor que no sigue su programa es 404 SIN_PROGRAMA; una cuenta que no se muestra o que no existe, 404 sin código")
    void elMentorQueNoSigueSuPrograma() throws Exception {
        HttpResponse<String> sinPrograma = get(delLider(mentorSinPrograma, "semana"), lider);

        assertThat(sinPrograma.statusCode()).isEqualTo(404);
        assertThat(codigo(sinPrograma)).isEqualTo("SIN_PROGRAMA");
        assertThat(cuerpo(sinPrograma).get("message").asText()).isNotBlank();
        HttpResponse<String> inexistente = get(delLider(UUID.randomUUID(), "semana"), lider);
        assertThat(inexistente.statusCode()).isEqualTo(404);
        assertThat(codigo(inexistente)).as("un id que no existe no dice nada").isNull();
        assertThat(get(delLider(dani, "semana"), lider).statusCode()).as("del Diplomado").isEqualTo(404);
        assertThat(codigo(get(delLider(dani, "semana"), lider))).isNull();
        assertThat(consultasDe(mentorSinPrograma)).as("lo que se rechaza no deja registro").isZero();
    }

    @Test
    @DisplayName("spec 056 FR-042: el mentor (también el de su grupo), la propia aprendiz y una cuenta suspendida reciben 403; Administración sí lee")
    void soloLoVeElLiderazgo() throws Exception {
        for (UUID actor : List.of(mentora, mentorConPrograma, ana, beto)) {
            assertThat(get(delLider(ana, "semana"), actor).statusCode()).as("actor " + actor).isEqualTo(403);
        }
        assertThat(get(delLider(ana, "semana"), admin).statusCode()).isEqualTo(200);
        String sesion = sesionDe(lider);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", lider);

        assertThat(get(delLider(ana, "semana"), sesion).statusCode()).as("líder suspendido con la sesión viva").isEqualTo(403);
    }

    @Test
    @DisplayName("spec 056 FR-014: abrir la semana deja la constancia de la consulta del líder, con el grupo y el mentor de la persona; sin grupo, en NULL (spec 005 FR-016)")
    void dejaLaConstanciaDeLaConsulta() throws Exception {
        assertThat(get(delLider(ana, "semana"), lider).statusCode()).isEqualTo(200);
        assertThat(get(delLider(beto, "semana"), lider).statusCode()).isEqualTo(200);

        Map<String, Object> deAna = jdbc.queryForMap("SELECT rol_consultor, grupo_id, mentor_id, consultor_id, semana_desde "
                + "FROM renaser.consultas_de_ficha WHERE persona_id = ?", ana);
        assertThat(deAna.get("rol_consultor")).isEqualTo("MENTOR_LEAD");
        assertThat(deAna.get("consultor_id")).isEqualTo(lider);
        assertThat(deAna.get("grupo_id")).isEqualTo(fenix);
        assertThat(deAna.get("mentor_id")).isEqualTo(mentora);
        assertThat(deAna.get("semana_desde").toString()).isEqualTo("2026-11-14");
        Map<String, Object> deBeto = jdbc.queryForMap("SELECT grupo_id, mentor_id FROM renaser.consultas_de_ficha WHERE persona_id = ?", beto);
        assertThat(deBeto.get("grupo_id")).isNull();
        assertThat(deBeto.get("mentor_id")).isNull();
    }
}
