package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase.ActivateSelfTrackingCommand;
import com.renaser.os.users.application.ports.in.participante.AssignMentorToTraineeUseCase;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParticipacionProgramaController.class)
@AutoConfigureMockMvc(addFilters = false)
class ParticipacionProgramaControllerTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivateSelfTrackingUseCase activateUseCase;
    @MockitoBean
    private DeactivateSelfTrackingUseCase deactivateUseCase;
    @MockitoBean
    private AssignMentorToTraineeUseCase assignMentorUseCase;
    @MockitoBean
    private ParticipacionProgramaFinder finder;

    @Test
    void statusDevuelveActiveTrueCuandoElActorEstaInscripto() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(finder.deParticipante(actorId)).thenReturn(Optional.of(
                new com.renaser.os.users.api.ParticipacionPrograma(actorId, true, 5, null,
                        java.time.ZoneId.of("America/Lima"), FasePrograma.PHASE_1_REBIRTH, null, null,
                        UserRole.MENTOR, false)));

        mockMvc.perform(get("/api/v1/mentor/activate-tracking").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void statusDevuelveActiveFalseCuandoElActorNoEstaInscripto() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(finder.deParticipante(actorId)).thenReturn(Optional.of(
                new com.renaser.os.users.api.ParticipacionPrograma(actorId, false, 0, null,
                        java.time.ZoneId.of("America/Lima"), FasePrograma.PHASE_1_REBIRTH, null, null,
                        UserRole.ADMIN, false)));

        mockMvc.perform(get("/api/v1/mentor/activate-tracking").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    /**
     * Blindaje anti mass-assignment (CLAUDE.MD §5.3.3): activate() no tiene
     * {@code @RequestBody} — un cliente que manda {@code programDay}/{@code diaPrograma}
     * en el cuerpo no tiene forma de que eso llegue al comando, porque el comando
     * solo tiene {@code actorId}. El servidor SIEMPRE fija dia 1.
     */
    @Test
    void activarIgnoraCualquierCampoDeProgresoQueMandeElClienteEnElCuerpo() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        ParticipacionPrograma creada = ParticipacionPrograma.activarSeguimientoPersonal(actorId, CLOCK);
        when(activateUseCase.activate(new ActivateSelfTrackingCommand(actorId))).thenReturn(creada);

        mockMvc.perform(post("/api/v1/mentor/activate-tracking")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programDay": 90, "diaPrograma": 90, "coherenceScore": 100, "currentPhase": "PHASE_4_ASCENSION"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programDay").value(1));

        verify(activateUseCase).activate(new ActivateSelfTrackingCommand(actorId));
    }

    @Test
    void activarYaInscriptoDevuelve409() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(activateUseCase.activate(any())).thenThrow(new IllegalStateException("Ya activaste"));

        mockMvc.perform(post("/api/v1/mentor/activate-tracking").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void activarComoRolSinPermisoDevuelve403() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(activateUseCase.activate(any())).thenThrow(new NotAuthorizedException("no autorizado"));

        mockMvc.perform(post("/api/v1/mentor/activate-tracking").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarDevuelveElResultadoDeIdempotencia() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(deactivateUseCase.deactivate(any())).thenReturn(false);

        mockMvc.perform(delete("/api/v1/mentor/activate-tracking").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deactivated").value(false));
    }

    @Test
    void asignarMentorSinMentorIdDevuelve400YNoLlegaAlCasoDeUso() throws Exception {
        mockMvc.perform(put("/api/v1/participants/{id}/mentor", UUID.randomUUID())
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(assignMentorUseCase);
    }

    @Test
    void asignarMentorComoNoAdministrativoDevuelve403() throws Exception {
        doThrow(new NotAuthorizedException("no autorizado")).when(assignMentorUseCase).assignMentor(any());

        mockMvc.perform(put("/api/v1/participants/{id}/mentor", UUID.randomUUID())
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mentorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void asignarMentorValidoDevuelve204() throws Exception {
        mockMvc.perform(put("/api/v1/participants/{id}/mentor", UUID.randomUUID())
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mentorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNoContent());
    }
}
