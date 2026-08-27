package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.users.application.ports.in.user.GetMyFullProfileUseCase.MyProfile;
import com.renaser.os.users.application.ports.in.user.GetMyFullProfileUseCase.TraineeProfileSummary;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;

/** Proyeccion a mano, no la entidad serializada (CLAUDE.MD §5.4.5/§8: evita fugas de campos). */
public record UserResponse(String id, String email, UserRole role, UserStatus status, String fullName,
                            String avatarUrl, String bio, String department,
                            TraineeProfileResponse traineeProfile) {

    /** Usado por el flujo de login (AutenticacionController) — sin enriquecer con
     * `traineeProfile`, que es exclusivo de {@code POST /api/v1/users/me}. */
    public static UserResponse from(User user) {
        return new UserResponse(user.id().toString(), user.email().value(), user.role(), user.status(),
                user.fullName(), user.avatarUrl(), user.bio(), user.department(), null);
    }

    /** Hueco #1 (docs/PLAN_INTEGRACION_FRONTEND.md): version enriquecida para
     * {@code POST /api/v1/users/me} — ver
     * {@link com.renaser.os.users.application.ports.in.user.GetMyFullProfileUseCase}. */
    public static UserResponse from(MyProfile profile) {
        User user = profile.user();
        return new UserResponse(user.id().toString(), user.email().value(), user.role(), user.status(),
                user.fullName(), user.avatarUrl(), user.bio(), user.department(),
                TraineeProfileResponse.from(profile.traineeProfile()));
    }

    /** Nombre distinto de {@code participante.TraineeProfileResponse} (que representa
     * {@code PATCH .../trainee-profile}, un contrato HTTP diferente) a proposito: este es
     * solo el bloque anidado dentro de {@code UserResponse}. */
    public record TraineeProfileResponse(String personalChallengeName, String startDate, String goalType,
                                          boolean isProgramCompleted, String programCompletedAt,
                                          int postProgramDay) {

        static TraineeProfileResponse from(TraineeProfileSummary summary) {
            if (summary == null) {
                return null;
            }
            return new TraineeProfileResponse(summary.personalChallengeName(),
                    summary.startDate() == null ? null : summary.startDate().toString(), summary.goalType(),
                    summary.isProgramCompleted(),
                    summary.programCompletedAt() == null ? null : summary.programCompletedAt().toString(),
                    summary.postProgramDay());
        }
    }
}
