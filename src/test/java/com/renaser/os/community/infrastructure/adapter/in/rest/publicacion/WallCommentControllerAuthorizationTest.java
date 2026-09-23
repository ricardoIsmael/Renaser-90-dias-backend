package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase.PaginaComentarios;
import com.renaser.os.community.application.ports.in.publicacion.EditarComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.OcultarComentarioUseCase;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/wall/{postId}/comments} estuvo "sin clasificar" (sin
 * {@code @RequiresPermission}) hasta E-215, y por eso {@code PermissionEnforcementInterceptor} lo
 * dejaba pasar sin mirar al actor: una cuenta SUSPENDIDA con la sesion todavia viva seguia leyendo
 * los comentarios del Muro. Mismo patron que {@link WallControllerAuthorizationTest}.
 *
 * <p>El actor sale de la sesion ({@code SecurityContextHolder}), no del header: el handler no
 * recibe actor, y el interceptor es quien lo resuelve para decidir.
 */
@WebMvcTest(WallCommentController.class)
@AutoConfigureMockMvc(addFilters = false)
class WallCommentControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @MockitoBean
    private ConsultarComentariosUseCase consultarUseCase;
    @MockitoBean
    private EscribirComentarioUseCase escribirUseCase;
    @MockitoBean
    private EditarComentarioUseCase editarUseCase;
    @MockitoBean
    private OcultarComentarioUseCase ocultarUseCase;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void sesionDe(UUID actorId, UserRole role, UserStatus status) {
        when(userSummaryFinder.findById(UserId.of(actorId)))
                .thenReturn(Optional.of(new UserSummary(UserId.of(actorId), "Actor", null, role, status)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null, List.of()));
    }

    @Test
    @DisplayName("TRAINEE activo con sesion lee los comentarios de una publicacion (USE_APP)")
    void traineeActivoLeeLosComentarios() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        sesionDe(actorId, UserRole.TRAINEE, UserStatus.ACTIVE);
        when(consultarUseCase.pagina(PublicacionId.of(postId), null))
                .thenReturn(new PaginaComentarios(List.of(), null, 0));

        mockMvc.perform(get("/api/v1/wall/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    /** Falla contra el codigo de antes de E-215: sin la anotacion el interceptor no evaluaba nada. */
    @Test
    @DisplayName("autorizacion negativa: un TRAINEE SUSPENDIDO con sesion valida recibe 403 al leer comentarios")
    void traineeSuspendidoNoLeeLosComentarios() throws Exception {
        UUID actorId = UUID.randomUUID();
        sesionDe(actorId, UserRole.TRAINEE, UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/wall/{postId}/comments", UUID.randomUUID()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarUseCase);
    }
}
