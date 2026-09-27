package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.SalirDeConversacionSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotoDelSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotoDelSoporteUseCase.FotoDelSoporte;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de la foto del chat de soporte (D-205). Filtros apagados, como
 * {@code MemoriaRenasiaControllerTest}; que sin sesión se rechace y que Spring Security no pise el
 * {@code Cache-Control} lo prueba {@code FotoDelSoporteIT} contra el Tomcat real.
 */
@WebMvcTest(ConversacionSoporteController.class)
@AutoConfigureMockMvc(addFilters = false)
class FotoDelSoporteControllerTest {

    private static final String FOTO = "/api/v1/chat/conversations/{id}/foto";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private VerFotoDelSoporteUseCase fotoUseCase;
    @MockitoBean
    private SalirDeConversacionSoporteUseCase salirUseCase;
    @MockitoBean
    private RellenarConversacionesDeSoporteUseCase rellenarUseCase;

    private final UUID soporte = UUID.randomUUID();

    private UUID actor(UserStatus estado) {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Ana", null, UserRole.TRAINEE, estado)));
        return actorId;
    }

    @Test
    @DisplayName("200: la tarjeta en image/jpeg, privada por un día y con el ETag de su huella")
    void laTarjeta() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(fotoUseCase.foto(UserId.of(actorId), ConversacionId.of(soporte))).thenReturn(new FotoDelSoporte(JPEG, "abc123"));

        mockMvc.perform(get(FOTO, soporte).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("ETag", "\"abc123\""))
                .andExpect(header().string("Cache-Control", allOf(containsString("max-age=86400"), containsString("private"))));
    }

    @Test
    @DisplayName("304 sin cuerpo si el teléfono ya tiene esa misma tarjeta (If-None-Match)")
    void noCambio() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(fotoUseCase.foto(UserId.of(actorId), ConversacionId.of(soporte))).thenReturn(new FotoDelSoporte(JPEG, "abc123"));

        mockMvc.perform(get(FOTO, soporte).header("X-Actor-Id", actorId.toString()).header("If-None-Match", "\"abc123\""))
                .andExpect(status().isNotModified())
                .andExpect(content().bytes(new byte[0]))
                .andExpect(header().string("ETag", "\"abc123\""));
    }

    @Test
    @DisplayName("403 a quien no participa, también si pide la imagen con Accept: image/jpeg")
    void sinAcceso() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(fotoUseCase.foto(UserId.of(actorId), ConversacionId.of(soporte)))
                .thenThrow(new NotAuthorizedException("No eres participante de esta conversación"));

        mockMvc.perform(get(FOTO, soporte).header("X-Actor-Id", actorId.toString())).andExpect(status().isForbidden());
        mockMvc.perform(get(FOTO, soporte).header("X-Actor-Id", actorId.toString()).accept(MediaType.IMAGE_JPEG))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("404 si no es un soporte o no existe")
    void noEsUnSoporte() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(fotoUseCase.foto(UserId.of(actorId), ConversacionId.of(soporte)))
                .thenThrow(new NoSuchElementException("Solo el chat de soporte tiene foto propia"));

        mockMvc.perform(get(FOTO, soporte).header("X-Actor-Id", actorId.toString())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("autorización negativa: una cuenta SUSPENDIDA recibe 403 y el caso de uso ni se llama")
    void suspendida() throws Exception {
        UUID actorId = actor(UserStatus.SUSPENDED);

        mockMvc.perform(get(FOTO, soporte).header("X-Actor-Id", actorId.toString())).andExpect(status().isForbidden());

        verifyNoInteractions(fotoUseCase);
    }
}
