package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.users.api.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull UserRole newRole) {
}
