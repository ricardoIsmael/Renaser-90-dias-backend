package com.renaser.os.academy.infrastructure.adapter.in.rest.clasediaria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.ClaseDiariaCompletada;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El shape que sale por el cable, no el del record: E-114 vivio justo en ese hueco. El test de
 * servicio ({@code ClaseDiariaServiceTest}) ya comprobaba que el caso de uso devuelve
 * {@code registroHabitoId}, pero nadie miraba como se serializaba, y el DTO web lo publicaba como
 * {@code habitTrackId}. El cliente movil valida la respuesta contra el contrato
 * (`docs/api/CONTRATO_CONTENIDO_IA.md` §1.11-bis) y la rechazaba entera, mostrando "No pudimos
 * enviar tu resumen" sobre un POST que habia funcionado.
 */
class CompletarClaseDiariaResponseTest {

    private final ObjectMapper json = new ObjectMapper();

    private static ClaseDiariaCompletada completada(UUID registroId) {
        return new ClaseDiariaCompletada(LeccionId.of("leccion-1"), registroId, 10);
    }

    @Test
    @DisplayName("el JSON usa las tres claves del contrato: leccionId, registroHabitoId y puntosOtorgados")
    void serializaConLasClavesDelContrato() throws Exception {
        UUID registroId = UUID.randomUUID();

        JsonNode cuerpo = json.readTree(json.writeValueAsString(CompletarClaseDiariaResponse.from(completada(registroId))));

        assertThat(cuerpo.get("leccionId").asText()).isEqualTo("leccion-1");
        assertThat(cuerpo.get("registroHabitoId").asText()).isEqualTo(registroId.toString());
        assertThat(cuerpo.get("puntosOtorgados").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("no sale ninguna clave fuera del contrato — habitTrackId era el nombre del backend viejo")
    void noPublicaClavesDeMas() throws Exception {
        JsonNode cuerpo = json.readTree(json.writeValueAsString(CompletarClaseDiariaResponse.from(completada(UUID.randomUUID()))));

        assertThat(cuerpo.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("leccionId", "registroHabitoId", "puntosOtorgados");
    }
}
