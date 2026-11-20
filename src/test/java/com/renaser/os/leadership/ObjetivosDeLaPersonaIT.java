package com.renaser.os.leadership;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /leadership/people/{p}/objetivos} de punta a punta (spec 056, FR-027): los objetivos de los 90 días, del mes y de la
 * semana de una persona, solo para leer, con lo que da {@code rocks.api.RocasDelAprendizFinder}.
 *
 * <p>Hoy es el día 45 del programa de Ana (Día 1 el 5 de octubre): el mes 2 y la semana 7.
 */
class ObjetivosDeLaPersonaIT extends EscenarioDelLiderIT {

    @Test
    @DisplayName("spec 056 FR-027: el líder lee los objetivos de los 90 días de un aprendiz con su avance, el del mes y el de la semana")
    void losObjetivosDeUnAprendiz() throws Exception {
        UUID trabajo = maestra(ana, "TRABAJO", "Facturar S/ 1500", "1500", "600", "S/");
        maestra(ana, "CUERPO", "Volver a correr", null, null, null);
        maestra(ana, "RELACIONES", "Cenar en familia", null, null, null);
        jdbc.update("INSERT INTO renaser.rocas_mensuales (roca_maestra_id, numero_mes, titulo) VALUES (?, 2, 'Cerrar 2 ventas')", trabajo);
        jdbc.update("INSERT INTO renaser.rocas_semanales (roca_maestra_id, numero_semana, titulo) VALUES (?, 7, 'Enviar 3 propuestas')", trabajo);

        HttpResponse<String> respuesta = get(delLider(ana, "objetivos"), lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode cuerpo = cuerpo(respuesta);
        assertThat(cuerpo.get("noventaDias")).extracting(o -> o.get("id").asText()).containsExactly("CUERPO", "TRABAJO", "RELACIONES");
        JsonNode delTrabajo = cuerpo.get("noventaDias").get(1);
        assertThat(delTrabajo.get("eje").asText()).isEqualTo("TRABAJO");
        assertThat(delTrabajo.get("titulo").asText()).isEqualTo("Facturar S/ 1500");
        assertThat(delTrabajo.get("avance").asInt()).as("600 de 1500").isEqualTo(40);
        assertThat(delTrabajo.get("meta").decimalValue()).isEqualByComparingTo("1500");
        assertThat(delTrabajo.get("valor").decimalValue()).isEqualByComparingTo("600");
        assertThat(delTrabajo.get("unidad").asText()).isEqualTo("S/");
        JsonNode delCuerpo = cuerpo.get("noventaDias").get(0);
        assertThat(delCuerpo.get("titulo").asText()).isEqualTo("Volver a correr");
        assertThat(delCuerpo.get("avance").isNull()).as("un objetivo sin meta no inventa un avance").isTrue();
        JsonNode mes = cuerpo.get("mes");
        assertThat(mes.get("numeroMes").asInt()).isEqualTo(2);
        assertThat(mes.get("objetivos")).anySatisfy(o -> {
            assertThat(o.get("eje").asText()).isEqualTo("TRABAJO");
            assertThat(o.get("titulo").asText()).isEqualTo("Cerrar 2 ventas");
        });
        assertThat(cuerpo.get("duracionDias").asInt()).as("spec 057 FR-001: el plazo de su formación").isEqualTo(90);
        assertThat(cuerpo.get("fase").get("numero").asInt()).as("día 45: fase 3, de 35 a 64").isEqualTo(3);
        assertThat(cuerpo.get("fase").get("primerDia").asInt()).isEqualTo(35);
        assertThat(cuerpo.get("fase").get("ultimoDia").asInt()).isEqualTo(64);
        JsonNode semana = cuerpo.get("semana");
        assertThat(semana.get("numeroSemana").asInt()).isEqualTo(7);
        assertThat(semana.get("objetivos")).hasSize(1);
        assertThat(semana.get("objetivos").get(0).get("eje").asText()).isEqualTo("TRABAJO");
        assertThat(semana.get("objetivos").get(0).get("titulo").asText()).isEqualTo("Enviar 3 propuestas");
        assertThat(semana.get("planificadas").asInt()).isZero();
        assertThat(semana.get("cumplidas").asInt()).isZero();
    }

    @Test
    @DisplayName("spec 056 FR-027: quien todavía no tiene objetivos, o no eligió su Día 1, responde 200 con las listas vacías y sin mes ni semana, nunca un 500")
    void sinObjetivos() throws Exception {
        UUID gaby = usuario("APRENDIZ", "Gaby Sin Día 1", "ACTIVO");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, timezone) "
                + "VALUES (?, 0, CURRENT_DATE + 1, 'America/Lima')", gaby);

        for (UUID persona : List.of(beto, gaby, mentorConPrograma)) {
            HttpResponse<String> respuesta = get(delLider(persona, "objetivos"), lider);

            assertThat(respuesta.statusCode()).as(persona + " " + respuesta.body()).isEqualTo(200);
            JsonNode cuerpo = cuerpo(respuesta);
            assertThat(cuerpo.get("noventaDias")).as(persona.toString()).isEmpty();
            assertThat(cuerpo.get("mes").isNull()).as(persona.toString()).isTrue();
            assertThat(cuerpo.get("semana").isNull()).as(persona.toString()).isTrue();
        }
    }

    @Test
    @DisplayName("spec 056 FR-027: un mentor que no sigue su programa es 404 SIN_PROGRAMA; el de otra formación o un id que no existe, 404 sin código")
    void sinProgramaYFueraDeLaFormacion() throws Exception {
        HttpResponse<String> sinPrograma = get(delLider(mentorSinPrograma, "objetivos"), lider);

        assertThat(sinPrograma.statusCode()).isEqualTo(404);
        assertThat(codigo(sinPrograma)).isEqualTo("SIN_PROGRAMA");
        assertThat(get(delLider(dani, "objetivos"), lider).statusCode()).as("del Diplomado").isEqualTo(404);
        assertThat(get(delLider(UUID.randomUUID(), "objetivos"), lider).statusCode()).as("no existe").isEqualTo(404);
        assertThat(get(delLider(dani, "objetivos"), admin).statusCode()).as("Administración ve las tres").isEqualTo(200);
    }

    @Test
    @DisplayName("spec 056 FR-027 FR-042: el mentor (también el de su grupo), la propia aprendiz y una cuenta suspendida reciben 403; solo se lee, no hay cómo escribir")
    void soloLoVeElLiderazgoYSoloLee() throws Exception {
        for (UUID actor : List.of(mentora, mentorConPrograma, ana, beto)) {
            assertThat(get(delLider(ana, "objetivos"), actor).statusCode()).as("actor " + actor).isEqualTo(403);
        }
        String sesion = sesionDe(lider);
        assertThat(pedir("POST", delLider(ana, "objetivos"), sesion, java.util.Map.of()).statusCode())
                .as("solo lectura: no hay POST").isIn(404, 405);
        assertThat(pedir("PUT", delLider(ana, "objetivos"), sesion, java.util.Map.of()).statusCode()).isIn(404, 405);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", lider);

        assertThat(get(delLider(ana, "objetivos"), sesion).statusCode()).as("líder suspendido con la sesión viva").isEqualTo(403);
    }

    /** Una Roca Maestra del eje; con meta (las tres cifras juntas) o cualitativa. */
    private UUID maestra(UUID persona, String eje, String objetivo, String meta, String avance, String unidad) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.rocas_maestras (id, participante_id, eje, objetivo, meta, avance, unidad) "
                + "VALUES (?, ?, ?::renaser.eje_objetivo, ?, CAST(? AS numeric), CAST(? AS numeric), ?)",
                id, persona, eje, objetivo, meta, avance, unidad);
        return id;
    }
}
