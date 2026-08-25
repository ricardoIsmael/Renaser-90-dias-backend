package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;

/**
 * DTO liviano expuesto entre modulos — nunca el User completo (CLAUDE.MD §5.1). Por
 * ejemplo, `habits` pregunta el rol/estado de un aprendiz sin ver su email ni su bio.
 */
public record UserSummary(UserId id, String fullName, String avatarUrl, UserRole role, UserStatus status) {
}
