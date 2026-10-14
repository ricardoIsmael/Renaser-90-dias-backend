package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.community.application.ports.in.celula.ConsultarCandidatosCelulaUseCase.MentorCandidato;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolPersonalizadoAsignado;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code GET /admin/cells/mentores}: el rol personalizado del mentor, aditivo (spec 002, fase 2; D-269). */
class MentorCandidatoResponseTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("con rol personalizado viaja { id, nombre } y role sigue siendo el base; sin él, null")
    void elRolPersonalizado() throws Exception {
        UUID rol = UUID.randomUUID();
        MentorCandidato conRol = new MentorCandidato(UserId.of(UUID.randomUUID()), "Mentor", null, null, List.of(),
                null, UserRole.MENTOR, new RolPersonalizadoAsignado(rol, "Mentor junior"));
        MentorCandidato sinRol = new MentorCandidato(UserId.of(UUID.randomUUID()), "Mentor", null, null, List.of(),
                null, UserRole.MENTOR);

        JsonNode cuerpo = json.readTree(json.writeValueAsString(MentorCandidatoResponse.from(conRol)));
        JsonNode sinCuerpo = json.readTree(json.writeValueAsString(MentorCandidatoResponse.from(sinRol)));

        assertThat(cuerpo.get("role").asText()).isEqualTo("MENTOR");
        assertThat(cuerpo.get("rolPersonalizado").get("id").asText()).isEqualTo(rol.toString());
        assertThat(cuerpo.get("rolPersonalizado").get("nombre").asText()).isEqualTo("Mentor junior");
        assertThat(sinCuerpo.get("rolPersonalizado").isNull()).isTrue();
    }
}
