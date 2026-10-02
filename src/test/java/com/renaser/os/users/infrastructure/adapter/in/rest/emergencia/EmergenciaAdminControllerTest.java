package com.renaser.os.users.infrastructure.adapter.in.rest.emergencia;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.in.emergencia.AtenderEmergenciaUseCase;
import com.renaser.os.users.application.ports.in.emergencia.AtenderEmergenciaUseCase.EmergenciaParaSoporte;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de la atención de pedidos de emergencia (D-244). */
@WebMvcTest(EmergenciaAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmergenciaAdminControllerTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-02T15:00:00Z"));
    private static final String ABIERTA = "/api/v1/admin/trainees/{id}/emergency-request";
    private static final String CERRAR = "/api/v1/admin/emergency-requests/{id}/close";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private AtenderEmergenciaUseCase atender;

    private final UUID aprendiz = UUID.randomUUID();

    private UUID actor(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id))).thenReturn(Optional.of(
                new UserSummary(UserId.of(id), "Alguien", null, rol, estado)));
        return id;
    }

    @Test
    @DisplayName("GET devuelve el pedido abierto con lo necesario para abrir «Cambiar día»")
    void abierta() throws Exception {
        UUID admin = actor(UserRole.ADMIN, UserStatus.ACTIVE);
        var s = SolicitudDeEmergencia.pedir(UUID.randomUUID(), UserId.of(aprendiz), "Accidente", 12, 18, CLOCK);
        when(atender.abiertaDe(UserId.of(admin), UserId.of(aprendiz)))
                .thenReturn(Optional.of(new EmergenciaParaSoporte(s, "Ana Pérez", 20)));

        mockMvc.perform(get(ABIERTA, aprendiz).header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(s.id().toString()))
                .andExpect(jsonPath("$.aprendizId").value(aprendiz.toString()))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.queOcurrio").value("Accidente"))
                .andExpect(jsonPath("$.diaPedido").value(12))
                .andExpect(jsonPath("$.diaAlPedir").value(18))
                .andExpect(jsonPath("$.diaActual").value(20));
    }

    @Test
    @DisplayName("GET sin pedido abierto es 204")
    void sinPedido() throws Exception {
        UUID admin = actor(UserRole.ALCHEMIST, UserStatus.ACTIVE);
        when(atender.abiertaDe(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get(ABIERTA, aprendiz).header("X-Actor-Id", admin.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST close la deja RESUELTA")
    void cerrar() throws Exception {
        UUID admin = actor(UserRole.ADMIN, UserStatus.ACTIVE);
        UUID id = UUID.randomUUID();
        var s = SolicitudDeEmergencia.pedir(id, UserId.of(aprendiz), "Accidente", 12, 18, CLOCK);
        s.cerrarSinCambio(UserId.of(admin), CLOCK);
        when(atender.cerrarSinCambio(UserId.of(admin), id)).thenReturn(s);

        mockMvc.perform(post(CERRAR, id).header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTA"))
                .andExpect(jsonPath("$.diaAplicado").isEmpty());
    }

    @Test
    @DisplayName("autorización negativa: APRENDIZ y ADMIN SUSPENDIDO reciben 403 sin llegar al caso de uso")
    void aprendizYSuspendidoSon403() throws Exception {
        UUID trainee = actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        UUID suspendido = actor(UserRole.ADMIN, UserStatus.SUSPENDED);

        for (UUID quien : new UUID[]{trainee, suspendido}) {
            mockMvc.perform(get(ABIERTA, aprendiz).header("X-Actor-Id", quien.toString()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(CERRAR, UUID.randomUUID()).header("X-Actor-Id", quien.toString()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(atender);
    }

    @Test
    @DisplayName("un MENTOR que el interceptor deja pasar recibe 403 del guard del caso de uso")
    void mentorEs403() throws Exception {
        UUID mentor = actor(UserRole.MENTOR, UserStatus.ACTIVE);
        when(atender.abiertaDe(eq(UserId.of(mentor)), any()))
                .thenThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran este panel"));

        mockMvc.perform(get(ABIERTA, aprendiz).header("X-Actor-Id", mentor.toString()))
                .andExpect(status().isForbidden());
    }
}
