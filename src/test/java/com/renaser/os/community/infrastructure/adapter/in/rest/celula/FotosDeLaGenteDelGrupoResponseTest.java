package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;
import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase.MiCelula;
import com.renaser.os.community.application.ports.in.celula.ConsultarMisCelulasUseCase.IntegranteDelGrupo;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>El shape que sale por el cable</b> para la lista de integrantes de la info del chat del grupo
 * (D-206, 2026-09-27): el mentor viaja con su id —la app lo compara con la sesión para decir «Tú»— y
 * con la ruta de su tarjeta; cada aprendiz, con la suya. Los ids van con el uuid pelado, no con el
 * {@code toString()} de un record, porque la app los compara tal cual con el id de la sesión.
 */
class FotosDeLaGenteDelGrupoResponseTest {

    private static final String RUTA_DEL_MENTOR = "/api/v1/chat/conversations/c/miembros/m/foto";

    private final ObjectMapper json = new ObjectMapper();
    private final UserId mentor = UserId.of(UUID.randomUUID());

    private static MiCelula grupo(PerfilBasico mentor, String rutaFotoMentor) {
        Celula celula = Celula.crear(CelulaId.of(UUID.randomUUID()), "Fénix", CohorteId.of(UUID.randomUUID()), null,
                Instant.EPOCH);
        Cohorte cohorte = Cohorte.crear(CohorteId.of(UUID.randomUUID()), "Cohorte", LocalDate.of(2026, 8, 1), null,
                Instant.EPOCH);
        return new MiCelula(celula, cohorte, mentor, 2, 1, rutaFotoMentor);
    }

    private JsonNode cable(Object respuesta) throws Exception {
        return json.readTree(json.writeValueAsString(respuesta));
    }

    @Test
    @DisplayName("/me/cells: el mentor viaja con su id (uuid pelado) y con la ruta de su tarjeta")
    void elMentorViajaConSuIdYSuTarjeta() throws Exception {
        MiCelula conMentor = grupo(new PerfilBasico(mentor, "Ricardo Palomino", null), RUTA_DEL_MENTOR);

        JsonNode cuerpo = cable(MiCelulaResponse.from(conMentor));

        assertThat(cuerpo.get("mentorId").asText()).isEqualTo(mentor.value().toString());
        assertThat(cuerpo.get("mentorPhotoPath").asText()).isEqualTo(RUTA_DEL_MENTOR);
        assertThat(cuerpo.get("mentorName").asText()).as("lo que ya viajaba no cambia").isEqualTo("Ricardo Palomino");
    }

    @Test
    @DisplayName("un grupo sin mentor manda los dos campos en null, sin romper")
    void sinMentorVanEnNull() throws Exception {
        JsonNode cuerpo = cable(MiCelulaResponse.from(grupo(null, null)));

        assertThat(cuerpo.get("mentorId").isNull()).isTrue();
        assertThat(cuerpo.get("mentorPhotoPath").isNull()).isTrue();
    }

    @Test
    @DisplayName("/me/cells/{id}/members: cada integrante con la ruta de su tarjeta; /me/cell/members, sin ruta")
    void cadaIntegranteConSuTarjeta() throws Exception {
        PerfilBasico ana = new PerfilBasico(UserId.of(UUID.randomUUID()), "Ana Pérez", null);
        String rutaDeAna = "/api/v1/chat/conversations/c/miembros/" + ana.id() + "/foto";

        JsonNode nuevo = cable(CellMemberResponse.from(new IntegranteDelGrupo(ana, rutaDeAna), true));
        JsonNode viejo = cable(CellMemberResponse.from(ana, true));

        assertThat(nuevo.get("traineeId").asText()).isEqualTo(ana.id().value().toString());
        assertThat(nuevo.get("photoPath").asText()).isEqualTo(rutaDeAna);
        assertThat(nuevo.get("isSelf").asBoolean()).isTrue();
        assertThat(viejo.get("photoPath").isNull()).as("el endpoint viejo no la calcula").isTrue();
    }
}
