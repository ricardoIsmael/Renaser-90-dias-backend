package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.GuiaVigente;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase.GuiasVigentes;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de la lectura de guías de recepción (D-242). Antes de esto la ruta solo aceptaba
 * PUT: un GET respondía 405 y la app no tenía forma de mostrar quién atiende.
 */
@WebMvcTest(MentoringPolicyController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReceptionGuidesControllerTest {

    private static final String RUTA = "/api/v1/admin/cohorts/{id}/reception/guides";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private ConfigurarMentoriaUseCase configurarMentoria;

    private final UUID cohorte = UUID.randomUUID();
    private final UUID recepcion = UUID.randomUUID();

    private UUID actor(UserRole rol, UserStatus estado) {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Alguien", null, rol, estado)));
        return actorId;
    }

    @Test
    @DisplayName("GET devuelve el grupo de recepcion y sus guias con nombre, rol y estado")
    void leeLosGuias() throws Exception {
        UUID admin = actor(UserRole.ADMIN, UserStatus.ACTIVE);
        UUID lider = UUID.randomUUID();
        when(configurarMentoria.consultarGuias(UserId.of(admin), CohorteId.of(cohorte), null))
                .thenReturn(new GuiasVigentes(cohorte, recepcion,
                        List.of(new GuiaVigente(lider, "Lia Lider", "MENTOR_LEAD", "ACTIVE"))));

        mockMvc.perform(get(RUTA, cohorte).header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receptionCellId").value(recepcion.toString()))
                .andExpect(jsonPath("$.guides[0].userId").value(lider.toString()))
                .andExpect(jsonPath("$.guides[0].fullName").value("Lia Lider"))
                .andExpect(jsonPath("$.guides[0].role").value("MENTOR_LEAD"))
                .andExpect(jsonPath("$.guides[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("receptionCellId en la consulta llega al caso de uso")
    void pasaElGrupoPedido() throws Exception {
        UUID admin = actor(UserRole.ALCHEMIST, UserStatus.ACTIVE);
        when(configurarMentoria.consultarGuias(UserId.of(admin), CohorteId.of(cohorte), recepcion))
                .thenReturn(new GuiasVigentes(cohorte, recepcion, List.of()));

        mockMvc.perform(get(RUTA, cohorte).param("receptionCellId", recepcion.toString())
                        .header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guides").isEmpty());
    }

    @Test
    @DisplayName("autorizacion negativa: APRENDIZ y cuenta SUSPENDIDA reciben 403 sin llegar al caso de uso")
    void aprendizYSuspendidaSon403() throws Exception {
        UUID aprendiz = actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        UUID suspendida = actor(UserRole.ADMIN, UserStatus.SUSPENDED);

        mockMvc.perform(get(RUTA, cohorte).header("X-Actor-Id", aprendiz.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA, cohorte).header("X-Actor-Id", suspendida.toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(configurarMentoria);
    }

    @Test
    @DisplayName("un MENTOR que el interceptor deja pasar recibe 403 del guard del caso de uso")
    void mentorEs403() throws Exception {
        UUID mentor = actor(UserRole.MENTOR, UserStatus.ACTIVE);
        when(configurarMentoria.consultarGuias(eq(UserId.of(mentor)), any(), isNull()))
                .thenThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST configuran la mentoria de una cohorte"));

        mockMvc.perform(get(RUTA, cohorte).header("X-Actor-Id", mentor.toString()))
                .andExpect(status().isForbidden());
    }
}
