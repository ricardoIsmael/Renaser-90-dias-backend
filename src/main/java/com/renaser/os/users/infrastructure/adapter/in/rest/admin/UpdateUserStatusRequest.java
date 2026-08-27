package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.users.api.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
