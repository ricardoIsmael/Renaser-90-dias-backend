package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.PaginaPublicaciones;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarReaccionesUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EditarPublicacionUseCase;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.application.ports.in.publicacion.EliminarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.OcultarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.RestaurarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.SolicitarUrlSubidaMediaUseCase;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integracion de A-1 CONTRA UN CONTROLLER REAL (CLAUDE.MD §0.2: todo adaptador
 * nuevo -incluido un interceptor web- necesita su prueba de integracion, no solo unitaria).
 * A diferencia de {@code PermissionEnforcementInterceptorTest} (que llama preHandle a mano),
 * esto prueba que Spring MVC de verdad registra el interceptor via
 * {@code PermissionEnforcementWebConfig} y lo ejecuta antes de {@link WallController} —
 * la pieza que un test unitario del interceptor solo no puede demostrar.
 *
 * <p>{@link WallController} se eligio porque ya tiene los tres casos que A-1 necesita
 * demostrar en un mismo controller: {@code USE_APP} (uno de los 8 permisos de TRAINEE),
 * {@code MODERATE_WALL} (fuera de la lista) y una ruta que exige el mismo permiso para un rol
 * sin matriz definida todavia (ADMIN, hueco temporal).
 */
@WebMvcTest(WallController.class)
@AutoConfigureMockMvc(addFilters = false)
class WallControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @MockitoBean
    private ConsultarFeedUseCase consultarFeedUseCase;
    @MockitoBean
    private PublicarUseCase publicarUseCase;
    @MockitoBean
    private EditarPublicacionUseCase editarUseCase;
    @MockitoBean
    private OcultarPublicacionUseCase ocultarUseCase;
    @MockitoBean
    private RestaurarPublicacionUseCase restaurarUseCase;
    @MockitoBean
    private EliminarPublicacionUseCase eliminarUseCase;
    @MockitoBean
    private ReaccionarUseCase reaccionarUseCase;
    @MockitoBean
    private ConsultarReaccionesUseCase consultarReaccionesUseCase;
    @MockitoBean
    private SolicitarUrlSubidaMediaUseCase solicitarUrlUseCase;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void mockActor(UUID actorId, UserRole role, UserStatus status) {
        when(userSummaryFinder.findById(UserId.of(actorId)))
                .thenReturn(Optional.of(new UserSummary(UserId.of(actorId), "Actor", null, role, status)));
    }

    @Test
    @DisplayName("TRAINEE activo puede leer el feed (USE_APP, uno de los 8 permisos otorgados)")
    void traineeActivoLeeElFeed() throws Exception {
        UUID actorId = UUID.randomUUID();
        mockActor(actorId, UserRole.TRAINEE, UserStatus.ACTIVE);
        when(consultarFeedUseCase.feed(UserId.of(actorId), null, null))
                .thenReturn(new PaginaPublicaciones(List.of(), null));

        mockMvc.perform(get("/api/v1/wall").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("autorizacion negativa: TRAINEE no puede restaurar publicaciones (exige MODERATE_WALL, fuera de sus 8 permisos) -> 403")
    void traineeNoPuedeRestaurar() throws Exception {
        UUID actorId = UUID.randomUUID();
        mockActor(actorId, UserRole.TRAINEE, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/wall/{id}/restore", UUID.randomUUID())
                        .header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restaurarUseCase);
    }

    @Test
    @DisplayName("autorizacion negativa: un TRAINEE SUSPENDIDO recibe 403 en un endpoint con permiso, aunque el token sea valido")
    void traineeSuspendidoRecibe403() throws Exception {
        UUID actorId = UUID.randomUUID();
        mockActor(actorId, UserRole.TRAINEE, UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/wall").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarFeedUseCase);
    }

    @Test
    @DisplayName("TEMPORAL (A-1): un ADMIN SI puede restaurar publicaciones, porque su matriz todavia no esta definida y falla-abierto")
    void adminPuedeRestaurarPorHuecoTemporal() throws Exception {
        UUID actorId = UUID.randomUUID();
        mockActor(actorId, UserRole.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/wall/{id}/restore", UUID.randomUUID())
                        .header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk());
    }

    // ─── GET /api/v1/wall/{id}/reactions — modal "Reacciones del post" (CLAUDE.MD §0.3) ───

    @Test
    @DisplayName("TRAINEE activo puede leer quien reacciono (USE_APP, mismo permiso que el feed)")
    void traineeActivoLeeLasReacciones() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        mockActor(actorId, UserRole.TRAINEE, UserStatus.ACTIVE);
        when(consultarReaccionesUseCase.reacciones(UserId.of(actorId), PublicacionId.of(postId)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/wall/{id}/reactions", postId)
                        .header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("autorizacion negativa: un TRAINEE SUSPENDIDO recibe 403 al pedir quien reacciono, aunque el token sea valido")
    void traineeSuspendidoNoPuedeLeerLasReacciones() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        mockActor(actorId, UserRole.TRAINEE, UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/wall/{id}/reactions", postId)
                        .header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarReaccionesUseCase);
    }
}
