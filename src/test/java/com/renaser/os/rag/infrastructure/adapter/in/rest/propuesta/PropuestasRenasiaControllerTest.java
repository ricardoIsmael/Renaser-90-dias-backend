package com.renaser.os.rag.infrastructure.adapter.in.rest.propuesta;

import com.renaser.os.rag.application.ports.in.propuesta.ResolverPropuestaUseCase;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.propuesta.PropuestaNoDisponibleException;
import com.renaser.os.shared.domain.NotAuthorizedException;
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

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de los botones de una propuesta (D-153). Filtros apagados, como
 * {@code WallControllerAuthorizationTest}: lo que se prueba aca es el interceptor de permisos, el
 * controller y el mapeo de errores de {@code GlobalExceptionHandler}. Que sin sesion se rechace lo
 * prueba {@link PropuestasRenasiaControllerAutenticacionTest}.
 */
@WebMvcTest(PropuestasRenasiaController.class)
@AutoConfigureMockMvc(addFilters = false)
class PropuestasRenasiaControllerTest {

    private static final String CONFIRMAR = "/api/v1/renasia/propuestas/{id}/confirmar";
    private static final String CANCELAR = "/api/v1/renasia/propuestas/{id}/cancelar";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private ResolverPropuestaUseCase resolverUseCase;

    private final UUID propuestaId = UUID.randomUUID();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private UUID actor(UserStatus status) {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Actor", null, UserRole.TRAINEE, status)));
        return actorId;
    }

    @Test
    @DisplayName("confirmar con exito devuelve 200 con estado CONFIRMADA y el mensaje legible")
    void confirmarConExito() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(resolverUseCase.confirmar(UserId.of(actorId), propuestaId))
                .thenReturn(ResultadoHerramienta.exito("Listo, marque Meditar."));

        mockMvc.perform(post(CONFIRMAR, propuestaId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.mensaje").value("Listo, marque Meditar."));
    }

    @Test
    @DisplayName("si el negocio rechaza al ejecutar, es 200 con estado FALLIDA y el motivo")
    void confirmarRechazadoPorElNegocio() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(resolverUseCase.confirmar(UserId.of(actorId), propuestaId))
                .thenReturn(ResultadoHerramienta.fallo("Ese habito ya vencio."));

        mockMvc.perform(post(CONFIRMAR, propuestaId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALLIDA"))
                .andExpect(jsonPath("$.mensaje").value("Ese habito ya vencio."));
    }

    @Test
    @DisplayName("autorizacion negativa: la propuesta de otra persona es 403")
    void propuestaAjenaEs403() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(resolverUseCase.confirmar(UserId.of(actorId), propuestaId))
                .thenThrow(new NotAuthorizedException("Esa propuesta no es tuya"));
        doThrow(new NotAuthorizedException("Esa propuesta no es tuya"))
                .when(resolverUseCase).cancelar(UserId.of(actorId), propuestaId);

        mockMvc.perform(post(CONFIRMAR, propuestaId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(CANCELAR, propuestaId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("autorizacion negativa: un usuario SUSPENDIDO recibe 403 y el caso de uso ni se llama")
    void suspendidoEs403() throws Exception {
        UUID actorId = actor(UserStatus.SUSPENDED);

        mockMvc.perform(post(CONFIRMAR, propuestaId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(CANCELAR, propuestaId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(resolverUseCase);
    }

    @Test
    @DisplayName("una propuesta vencida es 409 con un mensaje que la app puede mostrar tal cual")
    void vencidaEs409() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(resolverUseCase.confirmar(UserId.of(actorId), propuestaId)).thenThrow(new PropuestaNoDisponibleException(
                "Esta propuesta vencio. Pidele al acompanante que te la vuelva a ofrecer."));

        mockMvc.perform(post(CONFIRMAR, propuestaId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Esta propuesta vencio. Pidele al acompanante que te la vuelva a ofrecer."));
    }

    @Test
    @DisplayName("una propuesta que no existe es 404")
    void inexistenteEs404() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        when(resolverUseCase.confirmar(UserId.of(actorId), propuestaId))
                .thenThrow(new NoSuchElementException("No encontre esa propuesta"));

        mockMvc.perform(post(CONFIRMAR, propuestaId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("cancelar la propia es 204")
    void cancelarEs204() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);

        mockMvc.perform(post(CANCELAR, propuestaId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNoContent());

        verify(resolverUseCase).cancelar(UserId.of(actorId), propuestaId);
    }

    @Test
    @DisplayName("un id que no es UUID es 400, sin llegar al caso de uso")
    void idInvalidoEs400() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);

        mockMvc.perform(post(CONFIRMAR, "no-es-un-uuid").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(resolverUseCase);
    }
}
