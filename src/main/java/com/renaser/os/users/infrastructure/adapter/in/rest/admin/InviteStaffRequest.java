package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.users.api.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteStaffRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotNull UserRole role) {
}
