package com.renaser.os.users.infrastructure.adapter.in.rest.mentorprofile;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.users.application.ports.in.mentorprofile.UpdateMentorProfileUseCase;
import com.renaser.os.users.application.ports.in.mentorprofile.UpdateMentorProfileUseCase.UpdateMentorProfileCommand;
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

    public MentorProfileController(UpdateMentorProfileUseCase updateMentorProfileUseCase) {
        this.updateMentorProfileUseCase = updateMentorProfileUseCase;
    }

    @PatchMapping
    public ResponseEntity<Void> update(@PathVariable UUID mentorId, @ActorAutenticado UserId actor,
                                        @RequestBody UpdateMentorProfileRequest request) {
        updateMentorProfileUseCase.update(new UpdateMentorProfileCommand(UserId.of(mentorId), request.newLevel(),
                request.newOperationalStatus(), request.newBio(), actor));
        return ResponseEntity.noContent().build();
    }
}
