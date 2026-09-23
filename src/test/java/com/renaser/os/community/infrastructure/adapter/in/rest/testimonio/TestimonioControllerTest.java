package com.renaser.os.community.infrastructure.adapter.in.rest.testimonio;

import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase.CrearTestimonioCommand;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase.PromoverPublicacionCommand;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.testimonio.Testimonio;
import com.renaser.os.community.domain.model.testimonio.TestimonioId;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    /** Lo necesita {@code PermissionEnforcementInterceptor} para evaluar {@code listar} (USE_APP,
     * E-215). Sin este mock el interceptor ve el contexto reducido y deja pasar todo. */
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Testimonio testimonio(UserId autor) {
        return Testimonio.crear(TestimonioId.of(UUID.randomUUID()), autor, null, "Ana", "Aprendiz", null, null,
                "Cambio mi vida, en serio", 5, Instant.parse("2026-08-28T10:00:00Z"));
    }

    /**
     * <b>Invertida el 2026-09-18.</b> Esta prueba se llamaba
     * {@code crearSinSesionNiHeaderEsAnonimoYFunciona} y afirmaba que crear un testimonio SIN sesion
     * devuelve 201. Era correcta como descripcion de lo que el codigo hacia, y era exactamente el
     * agujero: {@code Testimonio.crear} marca {@code destacado = true} incondicionalmente y
     * {@code GET /api/v1/testimonios} lista los destacados, asi que cualquiera escribia en la
     * vitrina publica del producto con el nombre y el rol que quisiera, atribuido a quien quisiera
     * — y sin forma de retirarlo, porque ningun endpoint invoca {@code retirar()}.
     *
     * <p>La rama de al lado del mismo handler, {@code promover}, ya exigia ADMIN/ALCHEMIST. Ahora
     * las dos piden lo mismo, que es lo unico coherente: escriben en la misma tabla.
     */
    @Test
    void crearSinSesionEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/testimonios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"texto\":\"Cambio mi vida, en serio\",\"estrellas\":5}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(crearUseCase);
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

    // ─── GET /api/v1/testimonios — "sin clasificar" hasta E-215, hoy USE_APP ───

    private void sesionDe(UserId actor, UserStatus status) {
        when(userSummaryFinder.findById(actor))
                .thenReturn(Optional.of(new UserSummary(actor, "Actor", null, UserRole.TRAINEE, status)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.value().toString(), null, List.of()));
    }

    @Test
    @DisplayName("TRAINEE activo con sesion lista los testimonios destacados (USE_APP)")
    void traineeActivoListaLosTestimonios() throws Exception {
        UserId actor = UserId.of(UUID.randomUUID());
        sesionDe(actor, UserStatus.ACTIVE);
        when(consultarUseCase.listarDestacados())
                .thenReturn(List.of(new ConsultarTestimoniosUseCase.TestimonioVista(testimonio(null), null, null)));

        mockMvc.perform(get("/api/v1/testimonios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** Falla contra el codigo de antes de E-215: sin {@code @RequiresPermission} el interceptor
     * no evaluaba nada y el caso de uso se invocaba. */
    @Test
    @DisplayName("autorizacion negativa: un TRAINEE SUSPENDIDO con sesion valida recibe 403 al listar testimonios")
    void traineeSuspendidoNoListaLosTestimonios() throws Exception {
        sesionDe(UserId.of(UUID.randomUUID()), UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/testimonios"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarUseCase);
    }
}
