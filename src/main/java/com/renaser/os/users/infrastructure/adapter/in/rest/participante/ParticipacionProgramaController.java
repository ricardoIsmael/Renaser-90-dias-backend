package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase.ActivateSelfTrackingCommand;
import com.renaser.os.users.application.ports.in.participante.AssignMentorToTraineeUseCase;
import com.renaser.os.users.application.ports.in.participante.AssignMentorToTraineeUseCase.AssignMentorCommand;
import com.renaser.os.users.application.ports.in.participante.ConsultarSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.ConsultarSelfTrackingUseCase.ConsultarSelfTrackingQuery;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase.DeactivateSelfTrackingCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
 * <p>X-Actor-Id: TEMPORAL, ver nota de AccountRequestController — no usar en produccion.
 */
@RestController
public class ParticipacionProgramaController {

    private final ActivateSelfTrackingUseCase activateUseCase;
    private final DeactivateSelfTrackingUseCase deactivateUseCase;
    private final ConsultarSelfTrackingUseCase consultarUseCase;
    private final AssignMentorToTraineeUseCase assignMentorUseCase;

    public ParticipacionProgramaController(ActivateSelfTrackingUseCase activateUseCase,
                                            DeactivateSelfTrackingUseCase deactivateUseCase,
                                            ConsultarSelfTrackingUseCase consultarUseCase,
                                            AssignMentorToTraineeUseCase assignMentorUseCase) {
        this.activateUseCase = activateUseCase;
        this.deactivateUseCase = deactivateUseCase;
        this.consultarUseCase = consultarUseCase;
        this.assignMentorUseCase = assignMentorUseCase;
    }

    @GetMapping("/api/v1/mentor/activate-tracking")
    public SelfTrackingStatusResponse status(@RequestHeader("X-Actor-Id") String actorId) {
        boolean active = consultarUseCase.estaActivo(new ConsultarSelfTrackingQuery(UserId.of(actorId)));
        return new SelfTrackingStatusResponse(active);
    }

    @PostMapping("/api/v1/mentor/activate-tracking")
    public ActivateSelfTrackingResponse activate(@RequestHeader("X-Actor-Id") String actorId) {
        var participacion = activateUseCase.activate(new ActivateSelfTrackingCommand(UserId.of(actorId)));
        return ActivateSelfTrackingResponse.from(participacion);
    }

    @DeleteMapping("/api/v1/mentor/activate-tracking")
    public DeactivateSelfTrackingResponse deactivate(@RequestHeader("X-Actor-Id") String actorId) {
        boolean deactivated = deactivateUseCase.deactivate(new DeactivateSelfTrackingCommand(UserId.of(actorId)));
        return new DeactivateSelfTrackingResponse(deactivated);
    }

    /** Administrativo (ADMIN/ALCHEMIST) — "nadie activa/desactiva el programa de otro
     * salvo administrativo" (CLAUDE.MD) se extiende a la reasignacion de mentor. */
    @PutMapping("/api/v1/participants/{traineeId}/mentor")
    public ResponseEntity<Void> assignMentor(@PathVariable UUID traineeId,
                                              @RequestHeader("X-Actor-Id") String actorId,
                                              @RequestBody @Valid AssignMentorRequest request) {
        assignMentorUseCase.assignMentor(new AssignMentorCommand(UserId.of(actorId), UserId.of(traineeId),
                UserId.of(request.mentorId())));
        return ResponseEntity.noContent().build();
    }
}
