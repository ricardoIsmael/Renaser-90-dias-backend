package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El bloqueo del dueno (2026-10-27) con {@code renaser.rocks.exigir-mapa-activo=true}: hasta que el Mapa
 * este activo, el objetivo de 90 dias solo lo escribe la activacion del Mapa.
 */
@TestPropertySource(properties = "renaser.rocks.exigir-mapa-activo=true")
@DisplayName("D-284 con el bloqueo encendido: sin el Mapa activo, el objetivo no se define desde fuera del Mapa")
class ObjetivoYMapaExigidoIT extends ObjetivoYMapaITBase {

    @Test
    @DisplayName("definir el objetivo desde Plan sin Mapa activo: 409 MAPA_PENDIENTE y no se guarda nada")
    void noSePuedeDefinirDesdePlan() throws Exception {
        String token = aprendizConSesion();

        var cuerpo = conflicto(definir(token, "TRABAJO", OBJETIVO_DE_PLAN, 30000, 19500, null), "MAPA_PENDIENTE");

        assertThat(cuerpo.get("message").asText()).contains("Primero completa tu Mapa de Renacimiento");
        assertThat(rocaDe(token, "TRABAJO")).isNull();
    }

    @Test
    @DisplayName("la activacion del Mapa sigue pudiendo escribir y reintentar sus rocas; activo, queda fijo")
    void laActivacionDelMapaEscribeSinBloqueo() throws Exception {
        String token = aprendizConSesion();

        ok(definir(token, "TRABAJO", OBJETIVO_DEL_MAPA, 45000, 12000, "MAPA"));
        ok(definir(token, "TRABAJO", OBJETIVO_DEL_MAPA, 45000, 12000, "MAPA"));
        conflicto(definir(token, "TRABAJO", OBJETIVO_DE_PLAN, 30000, 19500, null), "MAPA_PENDIENTE");
        assertThat(rocaDe(token, "TRABAJO").get("objetivo").asText()).isEqualTo(OBJETIVO_DEL_MAPA);

        assertThat(completarElMapa(token).statusCode()).isEqualTo(204);

        conflicto(definir(token, "TRABAJO", OBJETIVO_DE_PLAN, 30000, 19500, null), "ROCA_MAESTRA_FIJA");
    }

    @Test
    @DisplayName("una roca de antes del bloqueo no se puede cambiar desde Plan, pero el Mapa la reemplaza")
    void laRocaDeAntesLaReemplazaElMapa() throws Exception {
        String token = aprendizConSesion();
        // Antes de encender el bloqueo Plan dejaba definirla: se simula con el origen del Mapa y se vuelve a
        // pedir desde Plan para comprobar que ya no se puede tocar.
        ok(definir(token, "TRABAJO", OBJETIVO_DE_PLAN, 30000, 19500, "MAPA"));

        conflicto(definir(token, "TRABAJO", OBJETIVO_DEL_MAPA, 45000, 12000, null), "MAPA_PENDIENTE");
        ok(definir(token, "TRABAJO", OBJETIVO_DEL_MAPA, 45000, 12000, "MAPA"));
        assertThat(rocaDe(token, "TRABAJO").get("objetivo").asText()).isEqualTo(OBJETIVO_DEL_MAPA);
    }
}
