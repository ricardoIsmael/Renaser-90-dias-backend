package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase.FotoDelChat;
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
 * Contrato HTTP de las tarjetas con nombre del chat: la del soporte (D-205) y la de cada integrante
 * (D-206). Filtros apagados, como {@code MemoriaRenasiaControllerTest}; que sin sesión se rechace y que
 * Spring Security no pise el {@code Cache-Control} lo prueba {@code FotosDelChatIT} contra el Tomcat real.
 */
@WebMvcTest(FotosDelChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class FotosDelChatControllerTest {

    private static final String FOTO_DEL_SOPORTE = "/api/v1/chat/conversations/{id}/foto";
    /** D-212: la de un grupo va por la misma ruta, con {@code ?v=} para el caché del teléfono. */
    private static final String FOTO_DEL_GRUPO = "/api/v1/chat/conversations/{id}/foto?v={v}";
    private static final String FOTO_DE_INTEGRANTE = "/api/v1/chat/conversations/{id}/miembros/{usuarioId}/foto";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private VerFotosDelChatUseCase fotos;

    private final UUID conversacion = UUID.randomUUID();
    private final UUID integrante = UUID.randomUUID();

    private UUID actor(UserStatus estado) {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Ana", null, UserRole.TRAINEE, estado)));
        return actorId;
    }

    @Test
    @DisplayName("soporte, 200: la tarjeta en image/jpeg, privada por un día y con el ETag de su huella")
    void laTarjetaDelSoporte() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(fotos.fotoDeLaConversacion(UserId.of(actorId), ConversacionId.of(conversacion))).thenReturn(new FotoDelChat(JPEG, "abc123"));

        mockMvc.perform(get(FOTO_DEL_SOPORTE, conversacion).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("ETag", "\"abc123\""))
                .andExpect(header().string("Cache-Control", allOf(containsString("max-age=86400"), containsString("private"))));
    }

    @Test
    @DisplayName("soporte, 304 sin cuerpo si el teléfono ya tiene esa misma tarjeta (If-None-Match)")
    void noCambio() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(fotos.fotoDeLaConversacion(UserId.of(actorId), ConversacionId.of(conversacion))).thenReturn(new FotoDelChat(JPEG, "abc123"));

        mockMvc.perform(get(FOTO_DEL_SOPORTE, conversacion).header("X-Actor-Id", actorId.toString())
                        .header("If-None-Match", "\"abc123\""))
                .andExpect(status().isNotModified())
                .andExpect(content().bytes(new byte[0]))
                .andExpect(header().string("ETag", "\"abc123\""));
    }

    @Test
    @DisplayName("soporte: 403 a quien no participa (también pidiendo Accept: image/jpeg) y 404 si no es un soporte")
    void rechazosDelSoporte() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        UUID otra = UUID.randomUUID();
        when(fotos.fotoDeLaConversacion(UserId.of(actorId), ConversacionId.of(conversacion)))
                .thenThrow(new NotAuthorizedException("No eres participante de esta conversación"));
        when(fotos.fotoDeLaConversacion(UserId.of(actorId), ConversacionId.of(otra)))
                .thenThrow(new NoSuchElementException("Solo el chat de soporte tiene foto propia"));

        mockMvc.perform(get(FOTO_DEL_SOPORTE, conversacion).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(FOTO_DEL_SOPORTE, conversacion).header("X-Actor-Id", actorId.toString())
                        .accept(MediaType.IMAGE_JPEG))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(FOTO_DEL_SOPORTE, otra).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("grupo con foto propia (D-212): la misma ruta con ?v= sirve su foto con los mismos encabezados")
    void laFotoPropiaDeUnGrupo() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(fotos.fotoDeLaConversacion(UserId.of(actorId), ConversacionId.of(conversacion)))
                .thenReturn(new FotoDelChat(JPEG, "fed789"));

        mockMvc.perform(get(FOTO_DEL_GRUPO, conversacion, 1790000000000L).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("ETag", "\"fed789\""))
                .andExpect(header().string("Cache-Control", allOf(containsString("max-age=86400"), containsString("private"))));
    }

    @Test
    @DisplayName("integrante, 200: su tarjeta con los mismos encabezados de caché, y 304 con su ETag (D-206)")
    void laTarjetaDeUnIntegrante() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(fotos.fotoDeIntegrante(UserId.of(actorId), ConversacionId.of(conversacion), UserId.of(integrante)))
                .thenReturn(new FotoDelChat(JPEG, "def456"));

        mockMvc.perform(get(FOTO_DE_INTEGRANTE, conversacion, integrante).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("ETag", "\"def456\""))
                .andExpect(header().string("Cache-Control", allOf(containsString("max-age=86400"), containsString("private"))));
        mockMvc.perform(get(FOTO_DE_INTEGRANTE, conversacion, integrante).header("X-Actor-Id", actorId.toString())
                        .header("If-None-Match", "\"def456\""))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("integrante: 403 a quien no puede ver el grupo; 404 si es la comunidad, un 1 a 1 o alguien que no es integrante")
    void rechazosDelIntegrante() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        UUID otro = UUID.randomUUID();
        when(fotos.fotoDeIntegrante(UserId.of(actorId), ConversacionId.of(conversacion), UserId.of(integrante)))
                .thenThrow(new NotAuthorizedException("No eres participante de esta conversación"));
        when(fotos.fotoDeIntegrante(UserId.of(actorId), ConversacionId.of(conversacion), UserId.of(otro)))
                .thenThrow(new NoSuchElementException("No es integrante de un grupo o soporte que puedas ver"));

        mockMvc.perform(get(FOTO_DE_INTEGRANTE, conversacion, integrante).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(FOTO_DE_INTEGRANTE, conversacion, otro).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un id de usuario que no es UUID es 400, sin llegar al caso de uso")
    void usuarioInvalidoEs400() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);

        mockMvc.perform(get(FOTO_DE_INTEGRANTE, conversacion, "no-es-un-uuid").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fotos);
    }

    @Test
    @DisplayName("autorización negativa: una cuenta SUSPENDIDA recibe 403 en las dos y el caso de uso ni se llama")
    void suspendida() throws Exception {
        UUID actorId = actor(UserStatus.SUSPENDED);

        mockMvc.perform(get(FOTO_DEL_SOPORTE, conversacion).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(FOTO_DE_INTEGRANTE, conversacion, integrante).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fotos);
    }
}
