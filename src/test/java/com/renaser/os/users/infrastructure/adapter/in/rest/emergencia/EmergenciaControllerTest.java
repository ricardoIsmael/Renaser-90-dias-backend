package com.renaser.os.users.infrastructure.adapter.in.rest.emergencia;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase;
import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase.MiEmergencia;
import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase.PedirAyudaCommand;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del botón de emergencia del aprendiz (D-244). */
@WebMvcTest(EmergenciaController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmergenciaControllerTest {

    private static final String RUTA = "/api/v1/me/emergency-request";
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-02T15:00:00Z"));

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private PedirAyudaPorEmergenciaUseCase pedirAyuda;

    private UUID actor(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id))).thenReturn(Optional.of(
                new UserSummary(UserId.of(id), "Alguien", null, rol, estado)));
        return id;
    }

    @Test
    @DisplayName("GET devuelve el día actual, hasta qué día puede pedir y el pedido abierto")
    void consultar() throws Exception {
        UUID aprendiz = actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        var abierta = SolicitudDeEmergencia.pedir(UUID.randomUUID(), UserId.of(aprendiz), "Accidente", 12, 20, CLOCK);
        when(pedirAyuda.consultar(UserId.of(aprendiz))).thenReturn(new MiEmergencia(20, 20, abierta));

        mockMvc.perform(get(RUTA).header("X-Actor-Id", aprendiz.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaActual").value(20))
                .andExpect(jsonPath("$.diaMaximo").value(20))
                .andExpect(jsonPath("$.abierta.diaPedido").value(12))
                .andExpect(jsonPath("$.abierta.estado").value("ABIERTA"));
    }

    @Test
    @DisplayName("POST crea el pedido (201) con lo que mandó la persona y su propia identidad")
    void pedir() throws Exception {
        UUID aprendiz = actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(pedirAyuda.pedir(any())).thenAnswer(inv -> {
            PedirAyudaCommand c = inv.getArgument(0);
            return SolicitudDeEmergencia.pedir(UUID.randomUUID(), c.actorId(), c.queOcurrio(), c.diaPedido(), 20, CLOCK);
        });

        mockMvc.perform(post(RUTA).header("X-Actor-Id", aprendiz.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queOcurrio\":\"Me operaron\",\"diaPedido\":12,\"aprendizId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diaPedido").value(12))
                .andExpect(jsonPath("$.diaAlPedir").value(20));

        ArgumentCaptor<PedirAyudaCommand> captor = ArgumentCaptor.forClass(PedirAyudaCommand.class);
        verify(pedirAyuda).pedir(captor.capture());
        assertThat(captor.getValue().actorId()).as("el aprendiz no se puede inyectar por el cuerpo")
                .isEqualTo(UserId.of(aprendiz));
        assertThat(captor.getValue().queOcurrio()).isEqualTo("Me operaron");
    }

    @Test
    @DisplayName("un texto de más de 280 es 400 y no llega al caso de uso")
    void validacion() throws Exception {
        UUID aprendiz = actor(UserRole.TRAINEE, UserStatus.ACTIVE);

        mockMvc.perform(post(RUTA).header("X-Actor-Id", aprendiz.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queOcurrio\":\"" + "a".repeat(281) + "\",\"diaPedido\":3}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(pedirAyuda);
    }

    /** Día 0 (respuesta del dueño, 02/10): sin día llega al caso de uso, que decide si corresponde. */
    @Test
    @DisplayName("sin día (Día 0) el pedido llega al caso de uso con el día vacío")
    void sinDia() throws Exception {
        UUID aprendiz = actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(pedirAyuda.pedir(any())).thenAnswer(inv -> {
            PedirAyudaCommand c = inv.getArgument(0);
            return SolicitudDeEmergencia.pedir(UUID.randomUUID(), c.actorId(), c.queOcurrio(), c.diaPedido(), 0, CLOCK);
        });

        mockMvc.perform(post(RUTA).header("X-Actor-Id", aprendiz.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"queOcurrio\":\"Me enfermé\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diaPedido").isEmpty())
                .andExpect(jsonPath("$.diaAlPedir").value(0));
    }

    @Test
    @DisplayName("un pedido abierto es 409")
    void conflicto() throws Exception {
        UUID aprendiz = actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(pedirAyuda.pedir(any())).thenThrow(new IllegalStateException("Ya nos pediste ayuda."));

        mockMvc.perform(post(RUTA).header("X-Actor-Id", aprendiz.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"queOcurrio\":\"x\",\"diaPedido\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya nos pediste ayuda."));
    }

    @Test
    @DisplayName("autorización negativa: un aprendiz SUSPENDIDO recibe 403 sin llegar al caso de uso")
    void suspendidoEs403() throws Exception {
        UUID suspendido = actor(UserRole.TRAINEE, UserStatus.SUSPENDED);

        mockMvc.perform(get(RUTA).header("X-Actor-Id", suspendido.toString())).andExpect(status().isForbidden());
        mockMvc.perform(post(RUTA).header("X-Actor-Id", suspendido.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"queOcurrio\":\"x\",\"diaPedido\":3}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedirAyuda);
    }

    @Test
    @DisplayName("un MENTOR que el interceptor deja pasar recibe 403 del guard del caso de uso")
    void staffEs403() throws Exception {
        UUID mentor = actor(UserRole.MENTOR, UserStatus.ACTIVE);
        when(pedirAyuda.pedir(any())).thenThrow(new NotAuthorizedException("El pedido de emergencia es para aprendices"));

        mockMvc.perform(post(RUTA).header("X-Actor-Id", mentor.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"queOcurrio\":\"x\",\"diaPedido\":3}"))
                .andExpect(status().isForbidden());
    }
}
