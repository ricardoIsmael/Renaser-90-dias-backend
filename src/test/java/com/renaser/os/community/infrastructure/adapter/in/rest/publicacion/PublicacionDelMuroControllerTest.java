package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.MediaFirmada;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarPublicacionUseCase.PublicacionParaLeer;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
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

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/wall/{id}} (spec 005, R-6): el contrato que lee la app —los nombres del Muro— y cómo se traducen
 * el 403 y el 404 del caso de uso. Que un aprendiz ajeno o un mentor que no acompaña reciban 403 lo prueba
 * {@code ConsultarPublicacionServiceTest} contra las reglas reales.
 */
@WebMvcTest(PublicacionDelMuroController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicacionDelMuroControllerTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PUBLICACION = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private ConsultarPublicacionUseCase consultarPublicacion;

    private void actor(UserRole rol, UserStatus estado) {
        when(userSummaryFinder.findById(UserId.of(ACTOR)))
                .thenReturn(Optional.of(new UserSummary(UserId.of(ACTOR), "Actor", null, rol, estado)));
    }

    @Test
    @DisplayName("200: id, autor, texto, fecha y la media con su URL, con los nombres del Muro y sin cache")
    void publicacion() throws Exception {
        actor(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        when(consultarPublicacion.publicacionDe(UserId.of(ACTOR), PublicacionId.of(PUBLICACION)))
                .thenReturn(new PublicacionParaLeer(PUBLICACION, UserId.of(ANA), "Ana Pérez", null, "Hoy completé mi ritual",
                        Instant.parse("2026-10-14T16:00:00Z"),
                        List.of(new MediaFirmada(URI.create("https://almacen/firmada"), "image/jpeg", 0))));

        mockMvc.perform(get("/api/v1/wall/{id}", PUBLICACION).header("X-Actor-Id", ACTOR.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.id").value(PUBLICACION.toString()))
                .andExpect(jsonPath("$.authorId").value(ANA.toString()))
                .andExpect(jsonPath("$.authorName").value("Ana Pérez"))
                .andExpect(jsonPath("$.text").value("Hoy completé mi ritual"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-14T16:00:00Z"))
                .andExpect(jsonPath("$.media[0].url").value("https://almacen/firmada"))
                .andExpect(jsonPath("$.media[0].mimeType").value("image/jpeg"));
    }

    @Test
    @DisplayName("autorizacion negativa: lo que el caso de uso niega es 403")
    void sinPermiso() throws Exception {
        actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        doThrow(new NotAuthorizedException("no")).when(consultarPublicacion).publicacionDe(any(), any());

        mockMvc.perform(get("/api/v1/wall/{id}", PUBLICACION).header("X-Actor-Id", ACTOR.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("una publicacion que no esta disponible es 404")
    void noDisponible() throws Exception {
        actor(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        doThrow(new NoSuchElementException("no esta")).when(consultarPublicacion).publicacionDe(any(), any());

        mockMvc.perform(get("/api/v1/wall/{id}", PUBLICACION).header("X-Actor-Id", ACTOR.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("una cuenta suspendida no pasa ni el interceptor: 403 antes del caso de uso")
    void cuentaSuspendida() throws Exception {
        actor(UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/wall/{id}", PUBLICACION).header("X-Actor-Id", ACTOR.toString()))
                .andExpect(status().isForbidden());
    }
}
