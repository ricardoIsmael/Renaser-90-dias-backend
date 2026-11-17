package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /mentor/groups/{g}/learners/{u}/dias} de punta a punta (spec 055): el color y el porcentaje de cada día, con la regla de
 * {@code ColorSemaforo} leída de {@code semaforo_dias}, recortado a [Día 1, hoy].
 */
class DiasDelAprendizIT extends EscenarioDelMentorIT {

    @Test
    @DisplayName("spec 055 FR-012: cada día trae su color y su porcentaje con la regla del semáforo; sin fila es PENDIENTE y sin nada programado, SIN_DATOS")
    void elColorDeCadaDia() throws Exception {
        diaDelSemaforo(ana, "2026-11-14", 10, 10);
        diaDelSemaforo(ana, "2026-11-15", 10, 7);
        diaDelSemaforo(ana, "2026-11-16", 10, 3);
        diaDelSemaforo(ana, "2026-11-17", 0, 0);

        HttpResponse<String> respuesta = get(delMentor(fenix, ana, "dias") + "?desde=2026-11-14&hasta=2026-11-18", mentora);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        JsonNode cuerpo = cuerpo(respuesta);
        assertThat(cuerpo.get("zona").asText()).isEqualTo("America/Lima");
        assertThat(cuerpo.get("diaUno").asText()).isEqualTo("2026-10-05");
        assertThat(cuerpo.get("dias")).hasSize(5);
        assertThat(texto(cuerpo, 0)).isEqualTo("2026-11-14 MEDIDO VERDE 100");
        assertThat(texto(cuerpo, 1)).isEqualTo("2026-11-15 MEDIDO AMARILLO 70");
        assertThat(texto(cuerpo, 2)).isEqualTo("2026-11-16 MEDIDO ROJO 30");
        assertThat(texto(cuerpo, 3)).isEqualTo("2026-11-17 SIN_DATOS SIN_DATOS null");
        assertThat(texto(cuerpo, 4)).as("hoy todavía no cierra").isEqualTo("2026-11-18 PENDIENTE SIN_DATOS null");
    }

    @Test
    @DisplayName("spec 055 FR-012: un día pausado trae PAUSADO y ningún porcentaje, aunque haya una fila vieja")
    void unDiaPausado() throws Exception {
        diaDelSemaforo(ana, "2026-11-10", 10, 10);
        jdbc.update("INSERT INTO renaser.semaforo_pausas (id, usuario_id, desde, hasta, creada_en) "
                + "VALUES (?, ?, DATE '2026-11-10', DATE '2026-11-11', now())", UUID.randomUUID(), ana);

        JsonNode cuerpo = cuerpo(get(delMentor(fenix, ana, "dias") + "?desde=2026-11-10&hasta=2026-11-11", mentora));

        assertThat(texto(cuerpo, 0)).isEqualTo("2026-11-10 PAUSADO SIN_DATOS null");
    }

    @Test
    @DisplayName("spec 055 FR-012 FR-017: el rango se recorta a [Día 1, hoy]; lo anterior al Día 1 y lo futuro no se devuelve")
    void seRecortaAlDiaUnoYAHoy() throws Exception {
        JsonNode cuerpo = cuerpo(get(delMentor(fenix, ana, "dias") + "?desde=2026-09-28&hasta=2026-10-07", mentora));
        assertThat(cuerpo.get("dias")).hasSize(3);
        assertThat(cuerpo.get("dias").get(0).get("fecha").asText()).isEqualTo("2026-10-05");

        JsonNode futuro = cuerpo(get(delMentor(fenix, ana, "dias") + "?desde=2026-11-19&hasta=2026-11-30", mentora));
        assertThat(futuro.get("dias")).isEmpty();
    }

    @Test
    @DisplayName("spec 055 FR-012: más de 62 días, o un rango al revés, es 400 RANGO_INVALIDO")
    void rangoInvalido() throws Exception {
        HttpResponse<String> largo = get(delMentor(fenix, ana, "dias") + "?desde=2026-10-01&hasta=2026-12-02", mentora);
        HttpResponse<String> alReves = get(delMentor(fenix, ana, "dias") + "?desde=2026-11-10&hasta=2026-11-01", mentora);

        assertThat(largo.statusCode()).isEqualTo(400);
        assertThat(codigo(largo)).isEqualTo("RANGO_INVALIDO");
        assertThat(alReves.statusCode()).isEqualTo(400);
        assertThat(codigo(alReves)).isEqualTo("RANGO_INVALIDO");
        assertThat(get(delMentor(fenix, ana, "dias") + "?desde=2026-10-01&hasta=2026-12-01", mentora).statusCode())
                .as("62 días justos sí").isEqualTo(200);
    }

    @Test
    @DisplayName("spec 055 FR-017 FR-028: otro mentor, el que ya no acompaña, el propio aprendiz y el líder reciben 403 NO_ACOMPANA o 403")
    void soloLoVeQuienAcompanaHoy() throws Exception {
        String ruta = delMentor(fenix, ana, "dias") + "?desde=2026-11-14&hasta=2026-11-18";

        assertThat(get(ruta, otraMentora).statusCode()).isEqualTo(403);
        assertThat(codigo(get(ruta, otraMentora))).isEqualTo("NO_ACOMPANA");
        assertThat(get(ruta, ana).statusCode()).isEqualTo(403);
        assertThat(get(ruta, lider).statusCode()).isEqualTo(403);
        terminarAsignacion(fenix, mentora);
        assertThat(get(ruta, mentora).statusCode()).as("mentor rotado").isEqualTo(403);
    }

    private static String texto(JsonNode cuerpo, int i) {
        JsonNode dia = cuerpo.get("dias").get(i);
        return dia.get("fecha").asText() + " " + dia.get("estado").asText() + " " + dia.get("color").asText() + " "
                + (dia.get("porcentaje").isNull() ? "null" : dia.get("porcentaje").asText());
    }
}
