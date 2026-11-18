package com.renaser.os.leadership;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /leadership/people/{p}/dias} de punta a punta (spec 056): el color y el porcentaje de cada día de una persona para el
 * líder, con el mismo semáforo y el mismo recorte a [Día 1, hoy] que el {@code /dias} del mentor.
 */
class DiasDeLaPersonaIT extends EscenarioDelLiderIT {

    private static final String RANGO = "?desde=2026-11-14&hasta=2026-11-18";

    @Test
    @DisplayName("spec 056 FR-018 SC-004: el líder recibe los mismos días, colores y porcentajes que el mentor de la persona, en la misma forma")
    void losMismosDiasQueElMentor() throws Exception {
        diaDelSemaforo(ana, "2026-11-14", 10, 10);
        diaDelSemaforo(ana, "2026-11-15", 10, 7);
        diaDelSemaforo(ana, "2026-11-16", 10, 3);
        diaDelSemaforo(ana, "2026-11-17", 0, 0);

        HttpResponse<String> delLider = get(delLider(ana, "dias") + RANGO, lider);
        HttpResponse<String> delMentor = get(delMentor(fenix, ana, "dias") + RANGO, mentora);

        assertThat(delLider.statusCode()).as(delLider.body()).isEqualTo(200);
        assertThat(delLider.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(cuerpo(delLider)).isEqualTo(cuerpo(delMentor));
        JsonNode cuerpo = cuerpo(delLider);
        assertThat(cuerpo.get("zona").asText()).isEqualTo("America/Lima");
        assertThat(cuerpo.get("diaUno").asText()).isEqualTo(DIA_UNO);
        assertThat(texto(cuerpo, 0)).isEqualTo("2026-11-14 MEDIDO VERDE 100");
        assertThat(texto(cuerpo, 1)).isEqualTo("2026-11-15 MEDIDO AMARILLO 70");
        assertThat(texto(cuerpo, 2)).isEqualTo("2026-11-16 MEDIDO ROJO 30");
        assertThat(texto(cuerpo, 3)).isEqualTo("2026-11-17 SIN_DATOS SIN_DATOS null");
        assertThat(texto(cuerpo, 4)).as("hoy todavía no cierra").isEqualTo("2026-11-18 PENDIENTE SIN_DATOS null");
    }

    @Test
    @DisplayName("spec 056 FR-017: el líder ve los días de quien no está en ningún grupo, desde su Día 1")
    void sinGrupo() throws Exception {
        diaDelSemaforo(beto, "2026-11-16", 10, 10);

        HttpResponse<String> respuesta = get(delLider(beto, "dias") + RANGO, lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(cuerpo(respuesta).get("dias")).hasSize(5);
        assertThat(texto(cuerpo(respuesta), 2)).isEqualTo("2026-11-16 MEDIDO VERDE 100");
    }

    @Test
    @DisplayName("spec 056 FR-017: el rango se recorta a [Día 1, hoy]; lo anterior al Día 1 y lo futuro no se devuelve")
    void seRecortaAlDiaUnoYAHoy() throws Exception {
        JsonNode alPrincipio = cuerpo(get(delLider(ana, "dias") + "?desde=2026-09-28&hasta=2026-10-07", lider));
        assertThat(alPrincipio.get("dias")).hasSize(3);
        assertThat(alPrincipio.get("dias").get(0).get("fecha").asText()).isEqualTo(DIA_UNO);

        JsonNode alFinal = cuerpo(get(delLider(ana, "dias") + "?desde=2026-11-17&hasta=2026-11-30", lider));
        assertThat(alFinal.get("dias")).hasSize(2);
        assertThat(alFinal.get("dias").get(1).get("fecha").asText()).as("hoy es el último").isEqualTo("2026-11-18");

        assertThat(cuerpo(get(delLider(ana, "dias") + "?desde=2026-11-19&hasta=2026-11-30", lider)).get("dias"))
                .as("solo días por venir").isEmpty();
    }

    @Test
    @DisplayName("spec 056 FR-017: más de 62 días, o un rango al revés, es 400 RANGO_INVALIDO; 62 días justos sí")
    void rangoInvalido() throws Exception {
        HttpResponse<String> largo = get(delLider(ana, "dias") + "?desde=2026-10-01&hasta=2026-12-02", lider);
        HttpResponse<String> alReves = get(delLider(ana, "dias") + "?desde=2026-11-10&hasta=2026-11-01", lider);

        assertThat(largo.statusCode()).isEqualTo(400);
        assertThat(codigo(largo)).isEqualTo("RANGO_INVALIDO");
        assertThat(alReves.statusCode()).isEqualTo(400);
        assertThat(codigo(alReves)).isEqualTo("RANGO_INVALIDO");
        assertThat(get(delLider(ana, "dias") + "?desde=2026-10-01&hasta=2026-12-01", lider).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("spec 056 FR-042: el mentor (también el de su grupo), la propia aprendiz y una cuenta suspendida reciben 403; Administración sí lee")
    void soloLoVeElLiderazgo() throws Exception {
        String ruta = delLider(ana, "dias") + RANGO;

        for (UUID actor : List.of(mentora, mentorConPrograma, ana, beto)) {
            assertThat(get(ruta, actor).statusCode()).as("actor " + actor).isEqualTo(403);
        }
        assertThat(get(ruta, admin).statusCode()).isEqualTo(200);
        String sesion = sesionDe(lider);
        assertThat(get(ruta, sesion).statusCode()).isEqualTo(200);

        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", lider);

        assertThat(get(ruta, sesion).statusCode()).as("líder suspendido con la sesión viva").isEqualTo(403);
    }

    @Test
    @DisplayName("spec 056 FR-042: quien es de otra formación o no existe responde 404 como si no existiera, sin revelar nada")
    void fueraDeSuAlcance() throws Exception {
        assertThat(get(delLider(dani, "dias") + RANGO, lider).statusCode()).as("del Diplomado").isEqualTo(404);
        assertThat(get(delLider(UUID.randomUUID(), "dias") + RANGO, lider).statusCode()).as("no existe").isEqualTo(404);
        assertThat(get(delLider(dani, "dias") + RANGO, admin).statusCode()).as("Administración ve las tres").isEqualTo(200);
    }

    @Test
    @DisplayName("spec 056 FR-017: el mentor que lleva su programa tiene sus días; el que no, 404 SIN_PROGRAMA")
    void elMentorConYSinPrograma() throws Exception {
        diaDelSemaforo(mentorConPrograma, "2026-11-16", 10, 10);

        HttpResponse<String> conPrograma = get(delLider(mentorConPrograma, "dias") + RANGO, lider);
        HttpResponse<String> sinPrograma = get(delLider(mentorSinPrograma, "dias") + RANGO, lider);

        assertThat(conPrograma.statusCode()).as(conPrograma.body()).isEqualTo(200);
        assertThat(texto(cuerpo(conPrograma), 2)).isEqualTo("2026-11-16 MEDIDO VERDE 100");
        assertThat(sinPrograma.statusCode()).isEqualTo(404);
        assertThat(codigo(sinPrograma)).isEqualTo("SIN_PROGRAMA");
    }

    private static String texto(JsonNode cuerpo, int i) {
        JsonNode dia = cuerpo.get("dias").get(i);
        return dia.get("fecha").asText() + " " + dia.get("estado").asText() + " " + dia.get("color").asText() + " "
                + (dia.get("porcentaje").isNull() ? "null" : dia.get("porcentaje").asText());
    }
}
