package com.renaser.os.users.infrastructure.adapter.in.rest.mentorprofile;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.mentorprofile.UpdateMentorProfileUseCase;
import com.renaser.os.users.application.ports.in.mentorprofile.UpdateMentorProfileUseCase.UpdateMentorProfileCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** X-Actor-Id: ver nota de AccountRequestController — TEMPORAL, no usar en produccion. */
@RestController
@RequestMapping("/api/v1/users/{mentorId}/mentor-profile")
public class MentorProfileController {

    private final UpdateMentorProfileUseCase updateMentorProfileUseCase;

    public MentorProfileController(UpdateMentorProfileUseCase updateMentorProfileUseCase) {
        this.updateMentorProfileUseCase = updateMentorProfileUseCase;
    }

    @PatchMapping
    public ResponseEntity<Void> update(@PathVariable UUID mentorId, @RequestHeader("X-Actor-Id") String actorId,
                                        @RequestBody UpdateMentorProfileRequest request) {
        updateMentorProfileUseCase.update(new UpdateMentorProfileCommand(UserId.of(mentorId), request.newLevel(),
                request.newOperationalStatus(), request.newBio(), UserId.of(actorId)));
        return ResponseEntity.noContent().build();
    }
}
