package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El caso exacto del issue #20 con el bloqueo apagado, que es el default de produccion: una aprendiz define
 * su objetivo de 90 dias desde Plan, llena el Mapa con otro objetivo y lo activa.
 *
 * <p>Falla contra el codigo anterior: el {@code PUT} de la activacion chocaba con 409
 * {@code ROCA_MAESTRA_FIJA} y el Mapa no quedaba activo.
 */
@DisplayName("D-284: el Mapa sin activar reemplaza al objetivo definido en Plan; activo, el objetivo es fijo")
class ObjetivoYMapaIT extends ObjetivoYMapaITBase {

    @Test
    @DisplayName("Plan define, el Mapa activa con otro objetivo: 200, la roca conserva su id y queda la del Mapa")
    void elMapaReemplazaAlObjetivoDePlanYDespuesQuedaFijo() throws Exception {
        String token = aprendizConSesion();
        ok(definir(token, "TRABAJO", OBJETIVO_DE_PLAN, 30000, 19500, null));
        JsonNode dePlan = rocaDe(token, "TRABAJO");
        assertThat(dePlan.get("objetivo").asText()).isEqualTo(OBJETIVO_DE_PLAN);

        // La activacion escribe la roca ANTES de marcar el Mapa como activo.
        JsonNode devuelta = ok(definir(token, "TRABAJO", OBJETIVO_DEL_MAPA, 45000, 12000, "MAPA"));
        assertThat(devuelta.get("objetivo").asText()).isEqualTo(OBJETIVO_DEL_MAPA);
        JsonNode delMapa = rocaDe(token, "TRABAJO");
        assertThat(delMapa.get("objetivo").asText()).isEqualTo(OBJETIVO_DEL_MAPA);
        assertThat(delMapa.get("meta").decimalValue()).isEqualByComparingTo("45000");
        assertThat(delMapa.get("avance").decimalValue()).isEqualByComparingTo("12000");
        assertThat(delMapa.get("lineaBase").decimalValue()).isEqualByComparingTo("12000");
        assertThat(delMapa.get("id").asText()).as("misma roca, no una segunda").isEqualTo(dePlan.get("id").asText());
        assertThat(delMapa.get("creadoEn").asText()).isEqualTo(dePlan.get("creadoEn").asText());

        // El reintento de la app repite el mismo PUT: idempotente.
        ok(definir(token, "TRABAJO", OBJETIVO_DEL_MAPA, 45000, 12000, "MAPA"));
        assertThat(rocaDe(token, "TRABAJO").get("actualizadoEn").asText())
                .isEqualTo(delMapa.get("actualizadoEn").asText());

        assertThat(completarElMapa(token).statusCode()).isEqualTo(204);

        // Una vez activo, la activacion repetida sigue valiendo y cualquier cambio vuelve a ser 409 (D-234).
        ok(definir(token, "TRABAJO", OBJETIVO_DEL_MAPA, 45000, 12000, "MAPA"));
        conflicto(definir(token, "TRABAJO", OBJETIVO_DE_PLAN, 30000, 19500, null), "ROCA_MAESTRA_FIJA");
        conflicto(definir(token, "TRABAJO", OBJETIVO_DE_PLAN, 30000, 19500, "MAPA"), "ROCA_MAESTRA_FIJA");
        assertThat(rocaDe(token, "TRABAJO").get("objetivo").asText()).isEqualTo(OBJETIVO_DEL_MAPA);
    }

    @Test
    @DisplayName("un eje con roca de Plan y otros sin ella: la activacion de los tres ejes termina bien")
    void laActivacionDeLosTresEjesConUnaRocaDePlan() throws Exception {
        String token = aprendizConSesion();
        ok(definir(token, "CUERPO", "Bajar de 82 a 75 kg", 75, 82, null));

        ok(definir(token, "CUERPO", "Pesar 74 kg para el dia 90", 74, 82, "MAPA"));
        ok(definir(token, "TRABAJO", OBJETIVO_DEL_MAPA, 45000, 12000, "MAPA"));
        ok(definir(token, "RELACIONES", "Cenar en familia cinco noches por semana", 5, 1, "MAPA"));

        assertThat(rocaDe(token, "CUERPO").get("objetivo").asText()).isEqualTo("Pesar 74 kg para el dia 90");
        assertThat(completarElMapa(token).statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("bloqueo apagado: un APK que no manda el origen puede activar su Mapa igual (compatibilidad)")
    void unApkAnteriorSinOrigenTambienPuedeActivar() throws Exception {
        String token = aprendizConSesion();
        ok(definir(token, "TRABAJO", OBJETIVO_DE_PLAN, 30000, 19500, null));

        JsonNode delMapa = ok(definir(token, "TRABAJO", OBJETIVO_DEL_MAPA, 45000, 12000, null));

        assertThat(delMapa.get("objetivo").asText()).isEqualTo(OBJETIVO_DEL_MAPA);
    }

    @Test
    @DisplayName("un origen que no es MAPA es un 400")
    void unOrigenDesconocidoEsUn400() throws Exception {
        String token = aprendizConSesion();

        assertThat(definir(token, "TRABAJO", OBJETIVO_DE_PLAN, 30000, 19500, "PLAN").statusCode()).isEqualTo(400);
    }
}
