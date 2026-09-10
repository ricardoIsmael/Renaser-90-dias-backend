package com.renaser.os.users.infrastructure.adapter.in.rest.mentorprofile;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.mentorprofile.SetMentorOperationalStatusUseCase;
import com.renaser.os.users.application.ports.in.mentorprofile.SetMentorOperationalStatusUseCase.SetMentorOperationalStatusCommand;
import com.renaser.os.users.application.ports.in.mentorprofile.UpdateMentorProfileUseCase;
import com.renaser.os.users.application.ports.in.mentorprofile.UpdateMentorProfileUseCase.UpdateMentorProfileCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Actor: ver nota de AccountRequestController — se resuelve desde la sesion, con respaldo
 * temporal por el header {@code X-Actor-Id}. */
@RestController
@RequestMapping("/api/v1/users/{mentorId}/mentor-profile")
public class MentorProfileController {

    private final UpdateMentorProfileUseCase updateMentorProfileUseCase;
    private final SetMentorOperationalStatusUseCase setMentorOperationalStatusUseCase;

    public MentorProfileController(UpdateMentorProfileUseCase updateMentorProfileUseCase,
                                    SetMentorOperationalStatusUseCase setMentorOperationalStatusUseCase) {
        this.updateMentorProfileUseCase = updateMentorProfileUseCase;
        this.setMentorOperationalStatusUseCase = setMentorOperationalStatusUseCase;
    }

    @RequiresPermission(value = Permission.MANAGE_MENTOR_PROFILE, scope = "editar SOLO la bio tambien lo puede el propio mentor; nivel, estado operativo y especialidad no")
    @PatchMapping
    public ResponseEntity<Void> update(@PathVariable UUID mentorId, @ActorAutenticado UserId actor,
                                        @RequestBody UpdateMentorProfileRequest request) {
        updateMentorProfileUseCase.update(new UpdateMentorProfileCommand(UserId.of(mentorId), request.newLevel(),
                request.newOperationalStatus(), request.newBio(), actor, request.especialidad()));
        return ResponseEntity.noContent().build();
    }

    /**
     * El semaforo operativo por separado, porque su permiso es otro: aca tambien llega el
     * MENTOR_LEAD (SDD 002, DL-09). El <b>nivel</b> sigue detras de {@code MANAGE_MENTOR_PROFILE}
     * en el PATCH de arriba.
     *
     * <p>Vive en {@code users} y no bajo {@code /api/v1/leadership/**} porque
     * {@code MentorProfile} es un agregado de ESTE modulo: un controller de otro modulo tendria
     * que llamar a un caso de uso ajeno, y un modulo solo puede importar {@code <otro>/api}
     * (regla 01). La alternativa era abrir un puerto de escritura publico en {@code users.api}
     * solo para esto; se prefirio no ampliar la superficie publica por una operacion.
     */
    @RequiresPermission(value = Permission.SET_MENTOR_OPERATIONAL_STATUS,
            scope = "el destinatario tiene que tener perfil de mentor; lo verifica el caso de uso")
    @PatchMapping("/operational-status")
    public ResponseEntity<Void> setOperationalStatus(@PathVariable UUID mentorId, @ActorAutenticado UserId actor,
                                                      @Valid @RequestBody SetMentorOperationalStatusRequest request) {
        setMentorOperationalStatusUseCase.setOperationalStatus(
                new SetMentorOperationalStatusCommand(UserId.of(mentorId), request.status(), actor));
        return ResponseEntity.noContent().build();
    }
}
