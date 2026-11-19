package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.RecordarConsultaAlMentorUseCase;
import com.renaser.os.leadership.application.ports.in.RecordarConsultaAlMentorUseCase.RecordarConsultaCommand;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.shared.domain.UserId;
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

/** {@code POST /api/v1/leadership/tickets/{t}/recordar} (spec 056, FR-029) en la capa web: 204, y los rechazos con su código. */
@WebMvcTest(RecordatorioDeConsultaController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecordatorioDeConsultaControllerTest {

    private final UUID ticket = UUID.randomUUID();
    private final String ruta = "/api/v1/leadership/tickets/" + ticket + "/recordar";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RecordarConsultaAlMentorUseCase recordar;
    @MockitoBean private UserSummaryFinder usuarios;

    private String lider() {
        UserId id = UserId.of(UUID.randomUUID());
        org.mockito.Mockito.when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Lucía Líder", null, UserRole.MENTOR_LEAD, UserStatus.ACTIVE)));
        return id.value().toString();
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029: recordar responde 204 sin cuerpo y pasa la consulta y la clave al caso de uso")
    void respondeNoContent() throws Exception {
        String actor = lider();

        mockMvc.perform(post(ruta).header("X-Actor-Id", actor).contentType(MediaType.APPLICATION_JSON).content("{\"operationKey\":\"k1\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        ArgumentCaptor<RecordarConsultaCommand> comando = ArgumentCaptor.forClass(RecordarConsultaCommand.class);
        verify(recordar).recordar(comando.capture());
        assertThat(comando.getValue().actorId().value().toString()).isEqualTo(actor);
        assertThat(comando.getValue().ticketId()).isEqualTo(ticket);
        assertThat(comando.getValue().claveOperacion()).isEqualTo("k1");
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029: una consulta ya respondida responde 409 con codigo YA_RESPONDIDA")
    void yaRespondidaEs409ConCodigo() throws Exception {
        String actor = lider();
        doThrow(RechazoConCodigoException.conflicto("YA_RESPONDIDA", "Esa consulta ya está respondida.")).when(recordar).recordar(any());

        mockMvc.perform(post(ruta).header("X-Actor-Id", actor).contentType(MediaType.APPLICATION_JSON).content("{\"operationKey\":\"k1\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("YA_RESPONDIDA"));
    }

    // spec 056 FR-029
    @Test
    @DisplayName("spec 056 FR-029: sin operationKey es 400 y no llega al caso de uso")
    void sinClaveEs400() throws Exception {
        String actor = lider();

        mockMvc.perform(post(ruta).header("X-Actor-Id", actor).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());

        verifyNoInteractions(recordar);
    }
}
