package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.participante.ActivateProgramUseCase;
import com.renaser.os.users.application.ports.in.participante.ActivateProgramUseCase.ActivateProgramCommand;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase.ActivateSelfTrackingCommand;
import com.renaser.os.users.application.ports.in.participante.AssignMentorToTraineeUseCase;
import com.renaser.os.users.application.ports.in.participante.AssignMentorToTraineeUseCase.AssignMentorCommand;
import com.renaser.os.users.application.ports.in.participante.ConsultarActivacionProgramaUseCase;
import com.renaser.os.users.application.ports.in.participante.ConsultarActivacionProgramaUseCase.ConsultarActivacionProgramaQuery;
import com.renaser.os.users.application.ports.in.participante.ConsultarSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.ConsultarSelfTrackingUseCase.ConsultarSelfTrackingQuery;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase.DeactivateSelfTrackingCommand;
import com.renaser.os.users.application.ports.in.participante.UpdateTraineeProfileUseCase;
import com.renaser.os.users.application.ports.in.participante.UpdateTraineeProfileUseCase.UpdateTraineeProfileCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ruta {@code /api/v1/mentor/activate-tracking} preservada TAL CUAL la consume la app
 * movil hoy (RenaserPlayStoreCopy/src/features/mentor/services/mentorService.ts) y tal
 * como la sirve el backend viejo (src/app/api/v1/mentor/activate-tracking/route.ts) —
 * vive en el paquete `participante` de `users` (el 4to agregado de este modulo, dueño
 * real de `participantes_programa`) aunque el path siga hablando de "mentor": el
 * contrato HTTP con el cliente no cambia solo porque el backend se reorganizo.
 *
 * <p>Autorizacion (self-only, roles MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST) es guard clause
 * DENTRO de {@code ParticipacionProgramaService} — el controller no decide nada de
 * negocio (CLAUDE.MD §5.4.6).
 *
 * <p>Actor: resuelto desde la sesion, con respaldo temporal por el header {@code X-Actor-Id}
 * — ver nota de AccountRequestController.
 */
@RestController
public class ParticipacionProgramaController {

    private final ActivateSelfTrackingUseCase activateUseCase;
    private final DeactivateSelfTrackingUseCase deactivateUseCase;
    private final ConsultarSelfTrackingUseCase consultarUseCase;
    private final AssignMentorToTraineeUseCase assignMentorUseCase;
    private final UpdateTraineeProfileUseCase updateTraineeProfileUseCase;
    private final ActivateProgramUseCase activateProgramUseCase;
    private final ConsultarActivacionProgramaUseCase consultarActivacionProgramaUseCase;

    public ParticipacionProgramaController(ActivateSelfTrackingUseCase activateUseCase,
                                            DeactivateSelfTrackingUseCase deactivateUseCase,
                                            ConsultarSelfTrackingUseCase consultarUseCase,
                                            AssignMentorToTraineeUseCase assignMentorUseCase,
                                            UpdateTraineeProfileUseCase updateTraineeProfileUseCase,
                                            ActivateProgramUseCase activateProgramUseCase,
                                            ConsultarActivacionProgramaUseCase consultarActivacionProgramaUseCase) {
        this.activateUseCase = activateUseCase;
        this.deactivateUseCase = deactivateUseCase;
        this.consultarUseCase = consultarUseCase;
        this.assignMentorUseCase = assignMentorUseCase;
        this.updateTraineeProfileUseCase = updateTraineeProfileUseCase;
        this.activateProgramUseCase = activateProgramUseCase;
        this.consultarActivacionProgramaUseCase = consultarActivacionProgramaUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "a proposito sin guard de rol staff, para que la app pueda consultar el estado antes de ofrecer la activacion")
    @GetMapping("/api/v1/mentor/activate-tracking")
    public SelfTrackingStatusResponse status(@ActorAutenticado UserId actor) {
        boolean active = consultarUseCase.estaActivo(new ConsultarSelfTrackingQuery(actor));
        return new SelfTrackingStatusResponse(active);
    }

    @RequiresPermission(Permission.TRACK_PROGRAM_AS_STAFF)
    @PostMapping("/api/v1/mentor/activate-tracking")
    public ActivateSelfTrackingResponse activate(@ActorAutenticado UserId actor) {
        var participacion = activateUseCase.activate(new ActivateSelfTrackingCommand(actor));
        return ActivateSelfTrackingResponse.from(participacion);
    }

    @RequiresPermission(Permission.TRACK_PROGRAM_AS_STAFF)
    @DeleteMapping("/api/v1/mentor/activate-tracking")
    public DeactivateSelfTrackingResponse deactivate(@ActorAutenticado UserId actor) {
        boolean deactivated = deactivateUseCase.deactivate(new DeactivateSelfTrackingCommand(actor));
        return new DeactivateSelfTrackingResponse(deactivated);
    }

    /** Administrativo (ADMIN/ALCHEMIST) — "nadie activa/desactiva el programa de otro
     * salvo administrativo" (CLAUDE.MD) se extiende a la reasignacion de mentor. */
    @RequiresPermission(Permission.ASSIGN_MENTOR)
    @PutMapping("/api/v1/participants/{traineeId}/mentor")
    public ResponseEntity<Void> assignMentor(@PathVariable UUID traineeId,
                                              @ActorAutenticado UserId actor,
                                              @RequestBody @Valid AssignMentorRequest request) {
        assignMentorUseCase.assignMentor(new AssignMentorCommand(actor, UserId.of(traineeId),
                UserId.of(request.mentorId())));
        return ResponseEntity.noContent().build();
    }

    /**
     * Hueco #1 (docs/PLAN_INTEGRACION_FRONTEND.md): {@code services/profile.ts#updateTraineeProfile}
     * del frontend real ya le pega a esta ruta exacta. Vive aca, no en {@code UserController},
     * porque el campo pertenece al 4to agregado de `users` (`participantes_programa`), no a
     * `usuarios` — mismo criterio que el resto de este controller.
     */
    @RequiresPermission(value = Permission.USE_APP, scope = "self por construccion: el endpoint no recibe traineeId")
    @PatchMapping("/api/v1/users/me/trainee-profile")
    public TraineeProfileResponse updateTraineeProfile(@ActorAutenticado UserId actor,
                                                         @RequestBody UpdateTraineeProfileRequest request) {
        var participacion = updateTraineeProfileUseCase.updateMyTraineeProfile(
                new UpdateTraineeProfileCommand(actor, request.personalChallengeName()));
        return TraineeProfileResponse.from(participacion);
    }

    /**
     * D-66: le dice a la app si tiene que mostrar la pantalla de "elegi tu Dia 1" y, si
     * todavia no eligio, las fechas validas — sin esto la app tendria que adivinar la
     * aritmetica de {@link #activateProgram} a ciegas (ver javadoc de
     * {@link ConsultarActivacionProgramaUseCase}). Ruta bajo {@code /onboarding} porque
     * asi la espera el cliente movil real (docs/GAPS_FRONTEND_BACKEND.md §7), aunque el
     * agregado viva en `users` — mismo criterio que {@code /api/v1/mentor/activate-tracking}
     * arriba.
     */
    @RequiresPermission(value = Permission.USE_APP, scope = "self por construccion: el endpoint no recibe traineeId")
    @GetMapping("/api/v1/onboarding/activate-program")
    public EstadoActivacionProgramaResponse estadoActivacion(@ActorAutenticado UserId actor) {
        var estado = consultarActivacionProgramaUseCase.consultarEstado(new ConsultarActivacionProgramaQuery(actor));
        return EstadoActivacionProgramaResponse.from(estado);
    }

    /**
     * D-66: elige el Dia 1 (hoy, +1, +2 o +3 en la zona del participante — nunca un
     * default). Rechaza con 409 si el programa ya estaba activado (self-validating en
     * el dominio, {@code ParticipacionPrograma.activarPrograma}) y con 400 si la fecha
     * cae fuera de la ventana — el rango exacto lo devuelve el mensaje, la app no tiene
     * que adivinarlo (aunque deberia haberlo consultado antes via el GET de arriba).
     */
    @RequiresPermission(value = Permission.USE_APP, scope = "self por construccion: el endpoint no recibe traineeId")
    @PostMapping("/api/v1/onboarding/activate-program")
    public ActivarProgramaResponse activateProgram(@ActorAutenticado UserId actor,
                                                    @RequestBody @Valid ActivarProgramaRequest request) {
        var participacion = activateProgramUseCase.activarPrograma(
                new ActivateProgramCommand(actor, request.startDate()));
        return ActivarProgramaResponse.from(participacion);
    }
}
