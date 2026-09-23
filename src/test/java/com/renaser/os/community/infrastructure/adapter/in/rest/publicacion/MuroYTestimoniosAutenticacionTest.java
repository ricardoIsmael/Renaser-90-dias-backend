package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarReaccionesUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EditarComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EditarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EliminarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.OcultarComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.OcultarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.RestaurarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.SolicitarUrlSubidaMediaUseCase;
import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase;
import com.renaser.os.community.infrastructure.adapter.in.rest.testimonio.TestimonioController;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.SecurityConfig;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-215, con la cadena de seguridad REAL ({@code @Import(SecurityConfig.class)}, sin
 * {@code addFilters = false}). Un inventario reporto que {@code GET /api/v1/wall/mine} "con el
 * header X-Actor-Id devuelve el conteo de cualquier usuario". Estas pruebas fijan por que eso NO
 * pasa, para que no vuelva a pasar si alguien mueve las rutas:
 * <ul>
 *   <li>sin sesion, las cuatro rutas que estuvieron "sin clasificar" se cortan en el filtro con
 *       403, aunque venga el header;</li>
 *   <li>con sesion, el actor sale de la sesion y un {@code X-Actor-Id} ajeno se ignora.</li>
 * </ul>
 * Mismo patron que {@code RutasCerradasNoRompenAlUsuarioTest}.
 */
@WebMvcTest({WallController.class, WallCommentController.class, TestimonioController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class MuroYTestimoniosAutenticacionTest {

    private static final String HEADER_ACTOR_ID = "X-Actor-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @MockitoBean
    private ConsultarFeedUseCase consultarFeedUseCase;
    @MockitoBean
    private PublicarUseCase publicarUseCase;
    @MockitoBean
    private EditarPublicacionUseCase editarPublicacionUseCase;
    @MockitoBean
    private OcultarPublicacionUseCase ocultarPublicacionUseCase;
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
    @MockitoBean
    private ConsultarComentariosUseCase consultarComentariosUseCase;
    @MockitoBean
    private EscribirComentarioUseCase escribirComentarioUseCase;
    @MockitoBean
    private EditarComentarioUseCase editarComentarioUseCase;
    @MockitoBean
    private OcultarComentarioUseCase ocultarComentarioUseCase;
    @MockitoBean
    private ConsultarTestimoniosUseCase consultarTestimoniosUseCase;
    @MockitoBean
    private CrearTestimonioUseCase crearTestimonioUseCase;
    @MockitoBean
    private PromoverPublicacionATestimonioUseCase promoverUseCase;

    @Test
    @DisplayName("/wall/mine SIN sesion es 403 aunque mande X-Actor-Id: no hay conteo de nadie")
    void mineSinSesionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/wall/mine").header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarFeedUseCase);
    }

    @Test
    @DisplayName("/wall/latest-author SIN sesion es 403 aunque mande X-Actor-Id")
    void ultimoAutorSinSesionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/wall/latest-author").header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarFeedUseCase);
    }

    @Test
    @DisplayName("los comentarios de una publicacion SIN sesion son 403")
    void comentariosSinSesionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/wall/{postId}/comments", UUID.randomUUID())
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarComentariosUseCase);
    }

    @Test
    @DisplayName("los testimonios SIN sesion son 403: no son una vitrina publica (SecurityConfig lo decidio)")
    void testimoniosSinSesionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/testimonios"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarTestimoniosUseCase);
    }

    @Test
    @DisplayName("/wall/mine CON sesion cuenta las publicaciones de la sesion e ignora un X-Actor-Id ajeno")
    void mineConSesionIgnoraElHeaderAjeno() throws Exception {
        UUID sesion = UUID.randomUUID();
        UUID ajeno = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(sesion))).thenReturn(Optional.of(
                new UserSummary(UserId.of(sesion), "Ana", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(consultarFeedUseCase.contarMisPublicaciones(UserId.of(sesion))).thenReturn(2);

        mockMvc.perform(get("/api/v1/wall/mine")
                        .with(autenticadoComo(sesion))
                        .header(HEADER_ACTOR_ID, ajeno.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        verify(consultarFeedUseCase).contarMisPublicaciones(UserId.of(sesion));
        verify(consultarFeedUseCase, never()).contarMisPublicaciones(UserId.of(ajeno));
    }

    /** Un actor autenticado tal como lo deja la sesion real: el principal es el UUID. */
    private static RequestPostProcessor autenticadoComo(UUID actor) {
        return authentication(new UsernamePasswordAuthenticationToken(actor.toString(), "n/a", List.of()));
    }
}
