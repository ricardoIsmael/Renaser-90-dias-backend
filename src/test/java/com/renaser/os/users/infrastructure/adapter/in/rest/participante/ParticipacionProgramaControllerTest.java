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
import com.renaser.os.users.application.ports.in.participante.ConsultarSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.ConsultarSelfTrackingUseCase.ConsultarSelfTrackingQuery;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.UpdateTraineeProfileUseCase;
import com.renaser.os.users.application.ports.in.participante.UpdateTraineeProfileUseCase.UpdateTraineeProfileCommand;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private ConsultarSelfTrackingUseCase consultarUseCase;
    @MockitoBean
    private UpdateTraineeProfileUseCase updateTraineeProfileUseCase;

    @Test
    void statusDevuelveActiveTrueCuandoElActorEstaInscripto() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(consultarUseCase.estaActivo(new ConsultarSelfTrackingQuery(actorId))).thenReturn(true);

        mockMvc.perform(get("/api/v1/mentor/activate-tracking").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * E-38: el GET consultaba el finder publico directo, sin verificar al actor — un
     * {@code X-Actor-Id} inventado devolvia 200 {@code {"active":false}} en vez de 404,
     * a diferencia del POST/DELETE de la MISMA ruta que si lo verificaban.
     */
    @Test
    void statusConActorInexistenteDevuelve404YNoUn200Enganioso() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(consultarUseCase.estaActivo(new ConsultarSelfTrackingQuery(actorId)))
                .thenThrow(new java.util.NoSuchElementException("Usuario no encontrado: " + actorId));

        mockMvc.perform(get("/api/v1/mentor/activate-tracking").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusConActorSuspendidoDevuelve403() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(consultarUseCase.estaActivo(new ConsultarSelfTrackingQuery(actorId)))
                .thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        mockMvc.perform(get("/api/v1/mentor/activate-tracking").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusDevuelveActiveFalseCuandoElActorNoEstaInscripto() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(consultarUseCase.estaActivo(new ConsultarSelfTrackingQuery(actorId))).thenReturn(false);

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

    /** Hueco #1: el frontend real (services/profile.ts#updateTraineeProfile) le pega a esta ruta exacta. */
    @Test
    void actualizarTraineeProfileDevuelveElRetoPersonalActualizado() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        ParticipacionPrograma participacion = ParticipacionPrograma.inscribirTraineeAprobado(actorId, CLOCK);
        participacion.renombrarRetoPersonal("Correr una maraton", CLOCK);
        when(updateTraineeProfileUseCase.updateMyTraineeProfile(
                new UpdateTraineeProfileCommand(actorId, "Correr una maraton"))).thenReturn(participacion);

        mockMvc.perform(patch("/api/v1/users/me/trainee-profile")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personalChallengeName\":\"Correr una maraton\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalChallengeName").value("Correr una maraton"))
                .andExpect(jsonPath("$.timezone").value("America/Lima"));
    }

    @Test
    void actualizarTraineeProfileSinPerfilDevuelve404() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(updateTraineeProfileUseCase.updateMyTraineeProfile(any()))
                .thenThrow(new java.util.NoSuchElementException("No tenes un perfil de programa activo para editar"));

        mockMvc.perform(patch("/api/v1/users/me/trainee-profile")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personalChallengeName\":\"Correr una maraton\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizarTraineeProfileComoSuspendidoDevuelve403() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(updateTraineeProfileUseCase.updateMyTraineeProfile(any()))
                .thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        mockMvc.perform(patch("/api/v1/users/me/trainee-profile")
                        .header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personalChallengeName\":\"Correr una maraton\"}"))
                .andExpect(status().isForbidden());
    }
}
