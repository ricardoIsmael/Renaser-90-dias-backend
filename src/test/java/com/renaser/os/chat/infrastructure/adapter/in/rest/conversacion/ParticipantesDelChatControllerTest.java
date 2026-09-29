package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.VerParticipantesDeConversacionUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerParticipantesDeConversacionUseCase.PaginaDeParticipantes;
import com.renaser.os.chat.application.ports.in.conversacion.VerParticipantesDeConversacionUseCase.ParticipanteDelChat;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.RolEnElChat;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de los integrantes de un chat. La sesión real y la base las prueba {@code ParticipantesDelChatIT}. */
@WebMvcTest(ParticipantesDelChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ParticipantesDelChatControllerTest {

    private static final String RUTA = "/api/v1/chat/conversations/{id}/participants";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private VerParticipantesDeConversacionUseCase participantes;

    private final UUID conversacion = UUID.randomUUID();

    private UserId actor() {
        UserId id = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(id)).thenReturn(Optional.of(
                new UserSummary(id, "Ana", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        return id;
    }

    @Test
    @DisplayName("200: total, página y filas; la ruta de la tarjeta sale de la conversación y del integrante, sin correo ni teléfono")
    void lasFilas() throws Exception {
        UserId actorId = actor();
        UserId otro = UserId.of(UUID.randomUUID());
        when(participantes.ver(actorId, ConversacionId.of(conversacion), "ka", 1, 20)).thenReturn(new PaginaDeParticipantes(
                List.of(new ParticipanteDelChat(otro, "Kelin", RolEnElChat.ADMIN, false, true, "https://f/k.jpg"),
                        new ParticipanteDelChat(actorId, "Ana", RolEnElChat.APRENDIZ, true, false, null)), 41, 1, 20));

        mockMvc.perform(get(RUTA + "?q=ka&page=1&size=20", conversacion).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(41))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.participants[0].userId").value(otro.toString()))
                .andExpect(jsonPath("$.participants[0].rol").value("ADMIN"))
                .andExpect(jsonPath("$.participants[0].fotoPath")
                        .value("/api/v1/chat/conversations/" + conversacion + "/miembros/" + otro + "/foto"))
                .andExpect(jsonPath("$.participants[0].email").doesNotExist())
                .andExpect(jsonPath("$.participants[0].phone").doesNotExist())
                .andExpect(jsonPath("$.participants[1].esUnoMismo").value(true))
                .andExpect(jsonPath("$.participants[1].fotoPath").value(nullValue()));
    }

    @Test
    @DisplayName("sin parámetros: página 0 y 50 por página")
    void losValoresPorDefecto() throws Exception {
        UserId actorId = actor();
        when(participantes.ver(actorId, ConversacionId.of(conversacion), null, 0, 50))
                .thenReturn(new PaginaDeParticipantes(List.of(), 0, 0, 50));

        mockMvc.perform(get(RUTA, conversacion).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    @DisplayName("403 a quien no puede ver la conversación; 404 si no existe")
    void losRechazos() throws Exception {
        UserId actorId = actor();
        UUID otra = UUID.randomUUID();
        when(participantes.ver(actorId, ConversacionId.of(conversacion), null, 0, 50))
                .thenThrow(new NotAuthorizedException("No eres participante de esta conversación"));
        when(participantes.ver(actorId, ConversacionId.of(otra), null, 0, 50))
                .thenThrow(new NoSuchElementException("Conversacion no encontrada"));

        mockMvc.perform(get(RUTA, conversacion).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA, otra).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound());
    }
}
