package com.renaser.os.leadership;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El cupo y los colores de cada mentor en el padrón y en su ficha, y «Por resolver» (spec 056, FR-002 a FR-004), de punta a punta contra
 * Postgres: el cupo sale de los aprendices que el mentor acompaña hoy sobre la suma de los cupos de sus grupos.
 */
class CupoYPorResolverIT extends EscenarioDelLiderIT {

    private static final String PADRON = "/api/v1/leadership/mentors";

    @Test
    @DisplayName("spec 056 FR-003: el padrón trae el cupo de cada mentor (aprendices que acompaña sobre la suma de los cupos de sus grupos) y sus aprendices por color")
    void elCupoYLosColoresEnElPadron() throws Exception {
        asignar(fenix, beto, "APRENDIZ");

        JsonNode luisa = delPadron(mentora);

        assertThat(luisa.get("cupo").get("acompanados").asInt()).isEqualTo(2);
        assertThat(luisa.get("cupo").get("maximo").asInt()).as("la capacidad de la política por defecto").isEqualTo(10);
        JsonNode colores = luisa.get("porColor");
        assertThat(colores.get("rojo").asInt()).isZero();
        assertThat(colores.get("ambar").asInt()).isZero();
        assertThat(colores.get("verde").asInt()).isZero();
        assertThat(colores.get("sinDatos").asInt()).as("todavía sin días medidos").isEqualTo(2);
        assertThat(luisa.get("traineeCount").asInt()).as("lo que ya traía el padrón no cambia").isEqualTo(2);
        assertThat(luisa.get("semaforo").get("source").asText()).isEqualTo("OK");
    }

    @Test
    @DisplayName("spec 056 FR-003: el máximo es la suma de los cupos de sus grupos: el propio de cada grupo si lo tiene y, si no, el de la política")
    void elMaximoSumaLosGrupos() throws Exception {
        jdbc.update("UPDATE renaser.celulas SET capacidad_maxima = 12 WHERE id = ?", fenix);
        UUID aurora = grupo("Aurora", mentora);
        asignar(aurora, mentora, "MENTOR");
        asignar(aurora, beto, "APRENDIZ");

        JsonNode cupo = delPadron(mentora).get("cupo");

        assertThat(cupo.get("acompanados").asInt()).isEqualTo(2);
        assertThat(cupo.get("maximo").asInt()).as("12 de Fénix + 10 de Aurora").isEqualTo(22);
    }

    @Test
    @DisplayName("spec 056 FR-004: un mentor sin grupo trae su cupo en 0 de 0 y ningún aprendiz por color, no un dato que falta")
    void unMentorSinGrupo() throws Exception {
        JsonNode marco = delPadron(mentorConPrograma);

        assertThat(marco.get("cupo").get("acompanados").asInt()).isZero();
        assertThat(marco.get("cupo").get("maximo").asInt()).isZero();
        assertThat(marco.get("porColor").get("sinDatos").asInt()).isZero();
        assertThat(marco.get("groups").get("items")).isEmpty();
    }

    @Test
    @DisplayName("spec 056 FR-003: la ficha del mentor trae el mismo cupo y los mismos colores que su fila del padrón")
    void laFichaDelMentor() throws Exception {
        asignar(fenix, beto, "APRENDIZ");

        HttpResponse<String> respuesta = get(PADRON + "/" + mentora, lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        JsonNode mentor = cuerpo(respuesta).get("mentor");
        assertThat(mentor.get("cupo")).isEqualTo(delPadron(mentora).get("cupo"));
        assertThat(mentor.get("porColor")).isEqualTo(delPadron(mentora).get("porColor"));
        assertThat(mentor.get("cupo").get("acompanados").asInt()).isEqualTo(2);
        assertThat(cuerpo(respuesta).get("haceSuPrograma")).as("lo que ya traía la ficha sigue").isNotNull();
    }

    @Test
    @DisplayName("spec 056 FR-002: «por resolver» cuenta los grupos en curso sin mentor de las formaciones del líder y no los de otra formación")
    void gruposSinMentor() throws Exception {
        grupo("Huérfano uno", null);
        grupo("Huérfano dos", null);
        grupo("Del Diplomado", null, "DIPLOMADO");

        JsonNode porResolver = cuerpo(porResolver(lider));

        assertThat(porResolver.get("gruposSinMentor").asInt()).isEqualTo(2);
        assertThat(cuerpo(porResolver(admin)).get("gruposSinMentor").asInt()).as("Administración ve las tres").isEqualTo(3);
    }

    @Test
    @DisplayName("spec 056 FR-002: «por resolver» cuenta las consultas sin responder de los aprendices de sus mentores y los días de la más vieja")
    void consultasSinResponder() throws Exception {
        ticket(ana, "2026-11-13T15:00:00Z");
        ticket(ana, "2026-11-17T15:00:00Z");
        ticket(beto, "2026-11-01T15:00:00Z");

        JsonNode consultas = cuerpo(porResolver(lider)).get("consultas");

        assertThat(consultas.get("cantidad").asInt()).as("Beto no está en ningún grupo: no le toca a ningún mentor").isEqualTo(2);
        assertThat(consultas.get("diasDeLaMasVieja").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("spec 056 FR-002: sin nada que resolver, «por resolver» viene en cero, sin días y sin mentor con mala evaluación")
    void nadaQueResolver() throws Exception {
        JsonNode porResolver = cuerpo(porResolver(lider));

        assertThat(porResolver.get("gruposSinMentor").asInt()).isZero();
        assertThat(porResolver.get("consultas").get("cantidad").asInt()).isZero();
        assertThat(porResolver.get("consultas").get("diasDeLaMasVieja").isNull()).isTrue();
        assertThat(porResolver.get("peorMentor").isNull()).as("una evaluación sin historial no es una mala evaluación").isTrue();
    }

    @Test
    @DisplayName("spec 056 FR-042: el padrón y «por resolver» los ven el líder y Administración; el mentor, el aprendiz y una cuenta suspendida reciben 403")
    void soloLoVeElLiderazgo() throws Exception {
        for (UUID actor : List.of(mentora, mentorConPrograma, ana, beto)) {
            assertThat(get(PADRON, actor).statusCode()).as("padrón, actor " + actor).isEqualTo(403);
            assertThat(porResolver(actor).statusCode()).as("por resolver, actor " + actor).isEqualTo(403);
        }
        assertThat(porResolver(admin).statusCode()).isEqualTo(200);
        String sesion = sesionDe(lider);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", lider);

        assertThat(get(PADRON + "/por-resolver", sesion).statusCode()).as("líder suspendido con la sesión viva").isEqualTo(403);
    }

    // ── ayudas ──────────────────────────────────────────────────────────────

    private HttpResponse<String> porResolver(UUID actor) throws Exception {
        return get(PADRON + "/por-resolver", actor);
    }

    /** La fila del mentor en el padrón que ve el líder. */
    private JsonNode delPadron(UUID mentor) throws Exception {
        HttpResponse<String> respuesta = get(PADRON, lider);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        for (JsonNode fila : cuerpo(respuesta).get("mentors")) {
            if (fila.get("userId").asText().equals(mentor.toString())) {
                return fila;
            }
        }
        throw new AssertionError("El mentor " + mentor + " no está en el padrón: " + respuesta.body());
    }

    /** Una consulta abierta del aprendiz, hecha en ese instante. */
    private void ticket(UUID aprendiz, String creadoEn) {
        jdbc.update("INSERT INTO renaser.tickets_mentor (id, participante_id, descripcion_bloqueo, soluciones_intentadas, "
                + "impacto_meta_smart, creado_en) VALUES (?, ?, 'No avanzo', 'Probé de todo', 'Mi meta', CAST(? AS timestamptz))",
                UUID.randomUUID(), aprendiz, creadoEn);
    }
}
