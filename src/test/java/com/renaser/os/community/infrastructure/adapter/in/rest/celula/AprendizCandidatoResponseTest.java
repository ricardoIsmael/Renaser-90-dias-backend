package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.community.application.ports.in.celula.ConsultarCandidatosCelulaUseCase.AprendizCandidato;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>El shape que sale por el cable</b> para el selector de aprendices (E-190, 2026-09-16).
 *
 * <p>Desde que la lista ofrece tambien a los que ya tienen grupo, el candidato viaja con
 * {@code cellId}. La clave y la forma del valor son el contrato acordado con el frontend, y es el
 * MISMO campo que ya publica {@code MentorCandidatoResponse} — si aca saliera {@code celulaActual},
 * o el {@code toString()} de un record ({@code CelulaId[value=...]}), la pantalla lo leeria como
 * "sin grupo" y el traslado volveria a ser invisible, esta vez sin ningun error a la vista.
 */
class AprendizCandidatoResponseTest {

    private final ObjectMapper json = new ObjectMapper();

    private static AprendizCandidato candidato(CelulaId celulaActual) {
        return new AprendizCandidato(UserId.of(UUID.randomUUID()), "Aprendiz Fixture", null, celulaActual);
    }

    @Test
    @DisplayName("el grupo actual viaja como cellId, con el uuid pelado")
    void publicaElGrupoActualComoCellId() throws Exception {
        CelulaId celula = CelulaId.of(UUID.randomUUID());

        JsonNode cuerpo = json.readTree(json.writeValueAsString(AprendizCandidatoResponse.from(candidato(celula))));

        assertThat(cuerpo.get("cellId").asText()).isEqualTo(celula.value().toString());
    }

    @Test
    @DisplayName("sin grupo, cellId viaja null — no se omite ni se rellena con vacio")
    void sinGrupoElCampoViajaNulo() throws Exception {
        JsonNode cuerpo = json.readTree(json.writeValueAsString(AprendizCandidatoResponse.from(candidato(null))));

        assertThat(cuerpo.has("cellId")).isTrue();
        assertThat(cuerpo.get("cellId").isNull()).isTrue();
    }

    @Test
    @DisplayName("no se publica ninguna clave de mas")
    void noPublicaClavesDeMas() throws Exception {
        JsonNode cuerpo = json.readTree(json.writeValueAsString(AprendizCandidatoResponse.from(candidato(null))));

        assertThat(cuerpo.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("userId", "fullName", "avatarUrl", "cellId");
    }
}
