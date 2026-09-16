package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.GetTraineeDetailUseCase;
import com.renaser.os.users.application.ports.in.participante.GetTraineeDetailUseCase.TraineeDetail;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ResumenTraineeAdmin;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase.SetProgramDayCommand;
import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato REST del panel admin de aprendices. <b>No existia</b> hasta D-82: el endpoint
 * {@code PUT /{id}/program-day} — el que mueve el dia de un aprendiz — no tenia ni una
 * sola prueba de capa web, ni siquiera de autorizacion negativa, pese a estar cubierto por
 * {@code @RequiresPermission(MANAGE_TRAINEES)} (regla 0.3 de CLAUDE.MD).
 */
@WebMvcTest(TraineeAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class TraineeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListTraineesUseCase listTraineesUseCase;
    @MockitoBean
    private GetTraineeDetailUseCase getTraineeDetailUseCase;
    @MockitoBean
    private SetTraineeProgramDayUseCase setTraineeProgramDayUseCase;

    private static TraineeDetail detalle(UserId traineeId, AjusteDiaPrograma ultimoAjuste) {
        User user = User.rehydrate(traineeId, new Email(traineeId + "@renaser.com"), UserRole.TRAINEE,
                UserStatus.ACTIVE, "Aprendiz Fixture", null, null, null, null);
        var participacion = new ParticipacionPrograma(traineeId, true, 34, LocalDate.of(2026, 9, 3),
                ZoneId.of("America/Lima"), FasePrograma.PHASE_3_ALCHEMIST_WARRIOR, null, null,
                UserRole.TRAINEE, false, true);
        return new TraineeDetail(user, participacion, ultimoAjuste);
    }

    // --- PUT /{id}/program-day ------------------------------------------

    @Test
    void fijarDiaDevuelve204YPropagaElMotivoAlCasoDeUso() throws Exception {
        UserId actor = UserId.of(UUID.randomUUID());
        UUID traineeId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/trainees/{id}/program-day", traineeId)
                        .header("X-Actor-Id", actor.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programDay\":34,\"motivo\":\"Viaje 03/09-09/09\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<SetProgramDayCommand> captor = ArgumentCaptor.forClass(SetProgramDayCommand.class);
        verify(setTraineeProgramDayUseCase).fijarDia(captor.capture());
        assertThat(captor.getValue().newProgramDay()).isEqualTo(34);
        assertThat(captor.getValue().motivo()).isEqualTo("Viaje 03/09-09/09");
        assertThat(captor.getValue().traineeId().value()).isEqualTo(traineeId);
    }

    /** El panel admin actual todavia manda solo `programDay`: no puede romperse (D-82). */
    @Test
    void fijarDiaSinMotivoSigueFuncionandoParaElPanelViejo() throws Exception {
        UserId actor = UserId.of(UUID.randomUUID());

        mockMvc.perform(put("/api/v1/admin/trainees/{id}/program-day", UUID.randomUUID())
                        .header("X-Actor-Id", actor.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programDay\":34}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<SetProgramDayCommand> captor = ArgumentCaptor.forClass(SetProgramDayCommand.class);
        verify(setTraineeProgramDayUseCase).fijarDia(captor.capture());
        assertThat(captor.getValue().motivo()).isNull();
    }

    /** Autorizacion negativa (regla 0.3): sin permiso, 403 y el caso de uso no se toca. */
    @Test
    void fijarDiaSinPermisoDevuelve403() throws Exception {
        doThrow(new NotAuthorizedException("No autorizado")).when(setTraineeProgramDayUseCase)
                .fijarDia(any());

        mockMvc.perform(put("/api/v1/admin/trainees/{id}/program-day", UUID.randomUUID())
                        .header("X-Actor-Id", UserId.of(UUID.randomUUID()).toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programDay\":34}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fijarDiaDeUnParticipanteInexistenteDevuelve404() throws Exception {
        doThrow(new NoSuchElementException("Participante no inscripto")).when(setTraineeProgramDayUseCase)
                .fijarDia(any());

        mockMvc.perform(put("/api/v1/admin/trainees/{id}/program-day", UUID.randomUUID())
                        .header("X-Actor-Id", UserId.of(UUID.randomUUID()).toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programDay\":34}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fijarDiaFueraDeRangoDevuelve400YNoLlegaAlCasoDeUso() throws Exception {
        mockMvc.perform(put("/api/v1/admin/trainees/{id}/program-day", UUID.randomUUID())
                        .header("X-Actor-Id", UserId.of(UUID.randomUUID()).toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programDay\":91}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(setTraineeProgramDayUseCase);
    }

    @Test
    void fijarDiaSinProgramDayDevuelve400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/trainees/{id}/program-day", UUID.randomUUID())
                        .header("X-Actor-Id", UserId.of(UUID.randomUUID()).toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"viaje\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(setTraineeProgramDayUseCase);
    }

    @Test
    void fijarDiaConMotivoMasLargoQueElTopeDevuelve400() throws Exception {
        String largo = "x".repeat(281);

        mockMvc.perform(put("/api/v1/admin/trainees/{id}/program-day", UUID.randomUUID())
                        .header("X-Actor-Id", UserId.of(UUID.randomUUID()).toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programDay\":34,\"motivo\":\"" + largo + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(setTraineeProgramDayUseCase);
    }

    // --- GET /{id} --------------------------------------------------------

    @Test
    void detalleDevuelveElUltimoAjusteCuandoLeMovieronElDia() throws Exception {
        UserId traineeId = UserId.of(UUID.randomUUID());
        UserId admin = UserId.of(UUID.randomUUID());
        var ajuste = AjusteDiaPrograma.rehydrate(UUID.randomUUID(), traineeId, 40, 34, 0, 6, "Viaje",
                admin, Instant.parse("2026-09-03T15:00:00Z"));
        when(getTraineeDetailUseCase.obtener(any())).thenReturn(detalle(traineeId, ajuste));

        mockMvc.perform(get("/api/v1/admin/trainees/{id}", traineeId.value())
                        .header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programDay").value(34))
                .andExpect(jsonPath("$.lastDayAdjustment.previousDay").value(40))
                .andExpect(jsonPath("$.lastDayAdjustment.newDay").value(34))
                .andExpect(jsonPath("$.lastDayAdjustment.adjustmentDays").value(6))
                .andExpect(jsonPath("$.lastDayAdjustment.motivo").value("Viaje"))
                .andExpect(jsonPath("$.lastDayAdjustment.adjustedBy").value(admin.toString()));
    }

    /** La enorme mayoria de los aprendices nunca tuvo un ajuste: el campo va nulo, no vacio. */
    @Test
    void detalleSinAjustesDevuelveElCampoEnNulo() throws Exception {
        UserId traineeId = UserId.of(UUID.randomUUID());
        when(getTraineeDetailUseCase.obtener(any())).thenReturn(detalle(traineeId, null));

        mockMvc.perform(get("/api/v1/admin/trainees/{id}", traineeId.value())
                        .header("X-Actor-Id", UserId.of(UUID.randomUUID()).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastDayAdjustment").doesNotExist());
    }

    @Test
    void detalleSinPermisoDevuelve403() throws Exception {
        when(getTraineeDetailUseCase.obtener(any())).thenThrow(new NotAuthorizedException("No autorizado"));

        mockMvc.perform(get("/api/v1/admin/trainees/{id}", UUID.randomUUID())
                        .header("X-Actor-Id", UserId.of(UUID.randomUUID()).toString()))
                .andExpect(status().isForbidden());
    }

    // --- GET / (listado de Personas) --------------------------------------

    /**
     * <b>E-191: cada fila dice de que rol es.</b>
     *
     * <p>Mientras el listado fue solo de aprendices, el rol se deducia de "en que lista aparecio".
     * Desde que trae los cinco roles, esa deduccion no existe y el dato tiene que viajar. La
     * ortografia es la de la BASE —{@code LIDER_MENTORES}, no {@code MENTOR_LEAD}—, que es el
     * contrato fijado para esta pantalla; contra el codigo viejo la clave {@code role} ni siquiera
     * existia.
     */
    @Test
    @DisplayName("listar(): cada fila viaja con su rol, con la etiqueta de la base")
    void elListadoDicePorCadaFilaDeQueRolEs() throws Exception {
        UserId mentor = UserId.of(UUID.randomUUID());
        UserId aprendiz = UserId.of(UUID.randomUUID());
        when(listTraineesUseCase.listar(any())).thenReturn(new ListTraineesUseCase.PaginaTrainees(
                java.util.List.of(
                        new ResumenTraineeAdmin(mentor, "Mentora Fixture", "mentora@renaser.com",
                                UserStatus.ACTIVE, 0, FasePrograma.paraDiaPrograma(0), null, null,
                                UserRole.MENTOR_LEAD),
                        new ResumenTraineeAdmin(aprendiz, "Aprendiz Fixture", "aprendiz@renaser.com",
                                UserStatus.ACTIVE, 34, FasePrograma.PHASE_3_ALCHEMIST_WARRIOR, null, null,
                                UserRole.TRAINEE)),
                2, 0, 20));

        mockMvc.perform(get("/api/v1/admin/trainees")
                        .header("X-Actor-Id", UserId.of(UUID.randomUUID()).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("LIDER_MENTORES"))
                .andExpect(jsonPath("$.content[1].role").value("APRENDIZ"));
    }
}
