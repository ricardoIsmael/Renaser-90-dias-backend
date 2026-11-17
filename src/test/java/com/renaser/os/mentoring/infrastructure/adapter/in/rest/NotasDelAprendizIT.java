package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las notas privadas del mentor por rol y de punta a punta (spec 055): quién escribe, quién corrige, quién lee y quién no ve nada.
 */
class NotasDelAprendizIT extends EscenarioDelMentorIT {

    private String notas(UUID grupo, UUID aprendiz) {
        return delMentor(grupo, aprendiz, "notas");
    }

    private JsonNode escribir(UUID quien, String texto) throws Exception {
        HttpResponse<String> respuesta = pedir("POST", notas(fenix, ana), quien, Map.of("texto", texto));
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(201);
        return cuerpo(respuesta);
    }

    @Test
    @DisplayName("spec 055 FR-023 FR-026: el acompañante vigente escribe una nota (se guarda recortada, con su autor) y la lectura la devuelve")
    void elMentorEscribeYLaLecturaLaDevuelve() throws Exception {
        JsonNode creada = escribir(mentora, "  Hablamos de su ritmo de estudio  ");

        assertThat(creada.get("texto").asText()).isEqualTo("Hablamos de su ritmo de estudio");
        assertThat(creada.get("autor").get("id").asText()).isEqualTo(mentora.toString());
        assertThat(creada.get("autor").get("nombre").asText()).isEqualTo("Luisa Rojas");
        assertThat(creada.get("puedeCorregir").asBoolean()).isTrue();
        assertThat(creada.get("corregidoEn").isNull()).isTrue();
        JsonNode lista = cuerpo(get(notas(fenix, ana), mentora));
        assertThat(lista.get("notas")).hasSize(1);
        assertThat(lista.get("notas").get(0).get("id").asText()).isEqualTo(creada.get("id").asText());
        assertThat(lista.get("notas").get(0).get("texto").asText()).isEqualTo("Hablamos de su ritmo de estudio");
    }

    @Test
    @DisplayName("spec 055 FR-027: las notas salen de la más nueva a la más vieja")
    void deLaMasNuevaALaMasVieja() throws Exception {
        escribir(mentora, "Primera");
        reloj.avanzar(Duration.ofMinutes(5));
        escribir(mentora, "Segunda");

        JsonNode lista = cuerpo(get(notas(fenix, ana), mentora)).get("notas");

        assertThat(lista).extracting(n -> n.get("texto").asText()).containsExactly("Segunda", "Primera");
    }

    @Test
    @DisplayName("spec 055 FR-026: una nota vacía o de más de 2000 caracteres es 400 NOTA_INVALIDA y no se guarda; de 2000 sí")
    void notaInvalida() throws Exception {
        for (String mala : new String[] {"", "   ", "x".repeat(2001)}) {
            HttpResponse<String> respuesta = pedir("POST", notas(fenix, ana), mentora, Map.of("texto", mala));
            assertThat(respuesta.statusCode()).isEqualTo(400);
            assertThat(codigo(respuesta)).isEqualTo("NOTA_INVALIDA");
        }
        assertThat(cuerpo(get(notas(fenix, ana), mentora)).get("notas")).isEmpty();
        assertThat(escribir(mentora, "x".repeat(2000)).get("texto").asText()).hasSize(2000);
    }

    @Test
    @DisplayName("spec 055 FR-025: el autor la corrige el mismo día y el texto anterior queda en las versiones")
    void elAutorCorrigeElMismoDia() throws Exception {
        UUID nota = UUID.fromString(escribir(mentora, "Primera").get("id").asText());

        HttpResponse<String> respuesta = pedir("PATCH", notas(fenix, ana) + "/" + nota, mentora, Map.of("texto", "Primera, corregida"));

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(cuerpo(respuesta).get("texto").asText()).isEqualTo("Primera, corregida");
        assertThat(cuerpo(respuesta).get("corregidoEn").isNull()).isFalse();
        assertThat(jdbc.queryForList("SELECT texto_anterior FROM renaser.notas_del_mentor_versiones WHERE nota_id = ?",
                String.class, nota)).containsExactly("Primera");
        assertThat(cuerpo(get(notas(fenix, ana), mentora)).get("notas").get(0).get("texto").asText())
                .as("lo que se escribe se lee").isEqualTo("Primera, corregida");
    }

    @Test
    @DisplayName("spec 055 FR-025: al día siguiente del autor es 409 YA_NO_SE_PUEDE_CORREGIR, aunque en UTC fuera el mismo día; la lectura dice puedeCorregir=false")
    void alDiaSiguienteYaNoSeCorrige() throws Exception {
        // 22:00 del martes 17 en Lima (03:00 UTC del 18): se escribe.
        reloj.fijar(Instant.parse("2026-11-18T03:00:00Z"));
        UUID nota = UUID.fromString(escribir(mentora, "De noche").get("id").asText());
        reloj.fijar(Instant.parse("2026-11-18T04:59:00Z"));
        assertThat(pedir("PATCH", notas(fenix, ana) + "/" + nota, mentora, Map.of("texto", "Todavía el mismo día")).statusCode())
                .as("23:59 en Lima").isEqualTo(200);

        reloj.fijar(Instant.parse("2026-11-18T05:00:00Z"));
        HttpResponse<String> tarde = pedir("PATCH", notas(fenix, ana) + "/" + nota, mentora, Map.of("texto", "Ya es otro día"));

        assertThat(tarde.statusCode()).isEqualTo(409);
        assertThat(codigo(tarde)).isEqualTo("YA_NO_SE_PUEDE_CORREGIR");
        assertThat(cuerpo(get(notas(fenix, ana), mentora)).get("notas").get(0).get("puedeCorregir").asBoolean()).isFalse();
        assertThat(cuerpo(get(notas(fenix, ana), mentora)).get("notas").get(0).get("texto").asText()).isEqualTo("Todavía el mismo día");
    }

    @Test
    @DisplayName("spec 055 FR-024 FR-025 SC-006: el mentor nuevo lee las notas del anterior sin poder corregirlas (403 NO_ES_EL_AUTOR); el anterior ya no ve ni escribe nada (403 NO_ACOMPANA)")
    void elMentorNuevoLeeYElAnteriorNoVeNada() throws Exception {
        UUID nota = UUID.fromString(escribir(mentora, "Nota de la anterior").get("id").asText());
        terminarAsignacion(fenix, mentora);
        UUID nueva = usuario("MENTOR", "Nora Nueva", "ACTIVO");
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?) ON CONFLICT DO NOTHING", nueva);
        jdbc.update("UPDATE renaser.celulas SET mentor_id = ? WHERE id = ?", nueva, fenix);
        asignarDesde(fenix, nueva, "MENTOR", "now() - interval '20 minutes'");

        JsonNode lista = cuerpo(get(notas(fenix, ana), nueva)).get("notas");
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("puedeCorregir").asBoolean()).isFalse();
        HttpResponse<String> ajena = pedir("PATCH", notas(fenix, ana) + "/" + nota, nueva, Map.of("texto", "Ajena"));
        assertThat(ajena.statusCode()).isEqualTo(403);
        assertThat(codigo(ajena)).isEqualTo("NO_ES_EL_AUTOR");

        for (HttpResponse<String> respuesta : new HttpResponse[] {get(notas(fenix, ana), mentora),
                pedir("POST", notas(fenix, ana), mentora, Map.of("texto", "Intruso")),
                pedir("PATCH", notas(fenix, ana) + "/" + nota, mentora, Map.of("texto", "Intruso"))}) {
            assertThat(respuesta.statusCode()).isEqualTo(403);
            assertThat(codigo(respuesta)).isEqualTo("NO_ACOMPANA");
        }
    }

    @Test
    @DisplayName("spec 055 FR-028: una mentora de otro grupo, la propia aprendiz, una cuenta suspendida y una nota de otro aprendiz no dan nada")
    void quienNoAcompanaNoTocaNada() throws Exception {
        UUID nota = UUID.fromString(escribir(mentora, "Privada").get("id").asText());
        UUID luis = usuario("APRENDIZ", "Luis Soto", "ACTIVO");
        programa(luis, "2026-10-05");
        asignar(fenix, luis, "APRENDIZ");

        assertThat(get(notas(fenix, ana), otraMentora).statusCode()).isEqualTo(403);
        assertThat(pedir("POST", notas(fenix, ana), otraMentora, Map.of("texto", "Intruso")).statusCode()).isEqualTo(403);
        assertThat(get(notas(aurora, ana), otraMentora).statusCode()).as("su grupo, pero otro aprendiz").isEqualTo(403);
        assertThat(get(notas(fenix, ana), ana).statusCode()).as("el aprendiz no las lee").isEqualTo(403);
        assertThat(pedir("POST", notas(fenix, ana), ana, Map.of("texto", "Me anoto")).statusCode()).isEqualTo(403);
        assertThat(pedir("PATCH", notas(fenix, luis) + "/" + nota, mentora, Map.of("texto", "Cruzada")).statusCode())
                .as("la nota es de Ana, no de Luis").isEqualTo(404);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", mentora);
        assertThat(get(notas(fenix, ana), mentora).statusCode()).as("cuenta suspendida").isEqualTo(403);
    }

    @Test
    @DisplayName("spec 055 FR-024 SC-006: el líder y Administración leen las notas sin poder corregirlas; el aprendiz y el mentor no tienen esas rutas")
    void elLiderYAdministracionSoloLeen() throws Exception {
        escribir(mentora, "Para el liderazgo");
        String delLider = "/api/v1/leadership/people/" + ana + "/notas";
        String deAdmin = "/api/v1/admin/trainees/" + ana + "/notas";

        for (String ruta : new String[] {delLider, deAdmin}) {
            UUID lector = ruta.equals(delLider) ? lider : admin;
            HttpResponse<String> respuesta = get(ruta, lector);
            assertThat(respuesta.statusCode()).as(ruta + ": " + respuesta.body()).isEqualTo(200);
            JsonNode lista = cuerpo(respuesta).get("notas");
            assertThat(lista).hasSize(1);
            assertThat(lista.get(0).get("texto").asText()).isEqualTo("Para el liderazgo");
            assertThat(lista.get(0).get("autor").get("nombre").asText()).isEqualTo("Luisa Rojas");
            assertThat(lista.get(0).get("puedeCorregir").asBoolean()).as("solo leen").isFalse();
        }
        for (UUID sinPermiso : new UUID[] {ana, mentora, otraMentora}) {
            assertThat(get(delLider, sinPermiso).statusCode()).as("líder, " + sinPermiso).isEqualTo(403);
            assertThat(get(deAdmin, sinPermiso).statusCode()).as("admin, " + sinPermiso).isEqualTo(403);
        }
        assertThat(get(deAdmin, lider).statusCode()).as("el líder no usa la ruta de Administración").isEqualTo(403);
        UUID suspendido = usuario("ADMIN", "Admin Suspendido", "SUSPENDIDO");
        assertThat(get(deAdmin, suspendido).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/admin/trainees/" + UUID.randomUUID() + "/notas", admin).statusCode()).as("no existe").isEqualTo(404);
    }

    @Test
    @DisplayName("spec 055 FR-025: la nota de una cuenta dada de baja sigue visible para el mentor, el líder y Administración, con «Cuenta eliminada» y sin poder corregirla")
    void laNotaSinAutorSeLeeComoCuentaEliminada() throws Exception {
        UUID nota = UUID.fromString(escribir(mentora, "Se queda").get("id").asText());
        jdbc.update("UPDATE renaser.notas_del_mentor SET autor_id = NULL WHERE id = ?", nota);

        for (HttpResponse<String> respuesta : new HttpResponse[] {get(notas(fenix, ana), mentora),
                get("/api/v1/leadership/people/" + ana + "/notas", lider), get("/api/v1/admin/trainees/" + ana + "/notas", admin)}) {
            assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
            JsonNode lista = cuerpo(respuesta).get("notas");
            assertThat(lista).hasSize(1);
            assertThat(lista.get(0).get("texto").asText()).isEqualTo("Se queda");
            assertThat(lista.get(0).get("autor").get("id").isNull()).isTrue();
            assertThat(lista.get(0).get("autor").get("nombre").asText()).isEqualTo("Cuenta eliminada");
            assertThat(lista.get(0).get("puedeCorregir").asBoolean()).isFalse();
        }
        HttpResponse<String> ajena = pedir("PATCH", notas(fenix, ana) + "/" + nota, mentora, Map.of("texto", "Nadie"));
        assertThat(ajena.statusCode()).isEqualTo(403);
        assertThat(codigo(ajena)).isEqualTo("NO_ES_EL_AUTOR");
    }
}
