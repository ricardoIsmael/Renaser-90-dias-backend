package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.AvisarAlMentorUseCase;
import com.renaser.os.leadership.application.ports.in.AvisarAlMentorUseCase.AvisarAlMentorCommand;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /api/v1/leadership/people/{p}/avisar-al-mentor} (spec 056, FR-019) en la capa web: 204, y los rechazos con su código. */
@WebMvcTest(AvisoAlMentorController.class)
@AutoConfigureMockMvc(addFilters = false)
class AvisoAlMentorControllerTest {

    private final UUID lider = UUID.randomUUID();
    private final UUID persona = UUID.randomUUID();
    private final String ruta = "/api/v1/leadership/people/" + persona + "/avisar-al-mentor";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AvisarAlMentorUseCase avisar;
    @MockitoBean private UserSummaryFinder usuarios;

    private String cuenta(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        org.mockito.Mockito.when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Alguien", null, rol, estado)));
        return id.value().toString();
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: el aviso responde 204 sin cuerpo y pasa texto y clave al caso de uso")
    void respondeNoContent() throws Exception {
        String actor = cuenta(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        mockMvc.perform(post(ruta).header("X-Actor-Id", actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Llámala hoy\",\"operationKey\":\"k1\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        ArgumentCaptor<AvisarAlMentorCommand> comando = ArgumentCaptor.forClass(AvisarAlMentorCommand.class);
        verify(avisar).avisar(comando.capture());
        assertThat(comando.getValue().actorId().value().toString()).isEqualTo(actor);
        assertThat(comando.getValue().personaId().value()).isEqualTo(persona);
        assertThat(comando.getValue().texto()).isEqualTo("Llámala hoy");
        assertThat(comando.getValue().claveOperacion()).isEqualTo("k1");
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: sin mentor responde 409 con codigo SIN_MENTOR")
    void sinMentorEs409ConCodigo() throws Exception {
        String actor = cuenta(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        doThrow(RechazoConCodigoException.conflicto("SIN_MENTOR", "Esta persona no tiene un mentor a quien avisar.")).when(avisar).avisar(any());

        mockMvc.perform(post(ruta).header("X-Actor-Id", actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Llámala hoy\",\"operationKey\":\"k1\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SIN_MENTOR"));
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: fuera de sus formaciones responde 403 con codigo FUERA_DE_SU_ALCANCE")
    void fueraDeSuAlcanceEs403ConCodigo() throws Exception {
        String actor = cuenta(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        doThrow(new NotAuthorizedException("Esa persona es de otra formación", NotAuthorizedException.FUERA_DE_SU_ALCANCE)).when(avisar).avisar(any());

        mockMvc.perform(post(ruta).header("X-Actor-Id", actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Llámala hoy\",\"operationKey\":\"k1\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FUERA_DE_SU_ALCANCE"));
    }

    // spec 056 FR-019
    @Test
    @DisplayName("spec 056 FR-019: un texto en blanco o sin clave es 400 y no llega al caso de uso")
    void textoEnBlancoEs400() throws Exception {
        String actor = cuenta(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        mockMvc.perform(post(ruta).header("X-Actor-Id", actor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"  \",\"operationKey\":\"k1\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(ruta).header("X-Actor-Id", actor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Hola\"}")).andExpect(status().isBadRequest());

        verifyNoInteractions(avisar);
    }
}
