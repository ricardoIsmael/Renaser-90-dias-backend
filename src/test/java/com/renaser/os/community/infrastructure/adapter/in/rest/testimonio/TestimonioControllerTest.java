package com.renaser.os.community.infrastructure.adapter.in.rest.testimonio;

import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase.CrearTestimonioCommand;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase.PromoverPublicacionCommand;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.testimonio.Testimonio;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre el fix de seguridad de esta sesion: promover ya no confia en el header
 * {@code X-Actor-Id} crudo (bypaseable) — usa {@code @ActorAutenticado}, que resuelve la
 * sesion real primero y solo cae al header cuando no hay sesion. El modo A (formulario
 * manual, sin {@code wallPostId}) sigue aceptando uso anonimo (docs/api/CONTRATO_COMUNIDAD.md
 * sec. 7.1) — eso no cambia.
 */
@WebMvcTest(TestimonioController.class)
@AutoConfigureMockMvc(addFilters = false)
class TestimonioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarTestimoniosUseCase consultarUseCase;
    @MockitoBean
    private CrearTestimonioUseCase crearUseCase;
    @MockitoBean
    private PromoverPublicacionATestimonioUseCase promoverUseCase;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Testimonio testimonio(UserId autor) {
        return Testimonio.crear(autor, null, "Ana", "Aprendiz", null, null, "Cambio mi vida, en serio", 5,
                Instant.parse("2026-08-28T10:00:00Z"));
    }

    @Test
    void crearSinSesionNiHeaderEsAnonimoYFunciona() throws Exception {
        when(crearUseCase.crear(new CrearTestimonioCommand(null, "Ana", null, "Cambio mi vida, en serio", 5)))
                .thenReturn(new ConsultarTestimoniosUseCase.TestimonioVista(testimonio(null), null, null));

        mockMvc.perform(post("/api/v1/testimonios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"texto\":\"Cambio mi vida, en serio\",\"estrellas\":5}"))
                .andExpect(status().isCreated());
    }

    @Test
    void promoverConSesionRealIgnoraUnHeaderXActorIdFalseadoAOtroUsuario() throws Exception {
        UserId sesionReal = UserId.of(UUID.randomUUID());
        UserId idFalseadoEnElHeader = UserId.of(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(sesionReal.value().toString(), null, List.of()));
        UUID wallPostId = UUID.randomUUID();
        when(promoverUseCase.promover(new PromoverPublicacionCommand(sesionReal, PublicacionId.of(wallPostId), 5)))
                .thenReturn(new ConsultarTestimoniosUseCase.TestimonioVista(testimonio(sesionReal), null, null));

        mockMvc.perform(post("/api/v1/testimonios")
                        .header("X-Actor-Id", idFalseadoEnElHeader.value().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wallPostId\":\"" + wallPostId + "\",\"estrellas\":5}"))
                .andExpect(status().isCreated());

        verify(promoverUseCase).promover(eq(new PromoverPublicacionCommand(sesionReal, PublicacionId.of(wallPostId), 5)));
        verify(promoverUseCase, never()).promover(eq(
                new PromoverPublicacionCommand(idFalseadoEnElHeader, PublicacionId.of(wallPostId), 5)));
    }

    @Test
    void promoverSinSesionYSinHeaderDevuelve403() throws Exception {
        UUID wallPostId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/testimonios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wallPostId\":\"" + wallPostId + "\",\"estrellas\":5}"))
                .andExpect(status().isForbidden());
    }
}
