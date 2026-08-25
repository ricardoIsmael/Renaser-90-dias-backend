package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;

/** Proyeccion a mano, no la entidad serializada (CLAUDE.MD §5.4.5/§8: evita fugas de campos). */
public record UserResponse(String id, String email, UserRole role, UserStatus status, String fullName,
                            String avatarUrl, String bio, String department) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id().toString(), user.email().value(), user.role(), user.status(),
                user.fullName(), user.avatarUrl(), user.bio(), user.department());
    }
}
