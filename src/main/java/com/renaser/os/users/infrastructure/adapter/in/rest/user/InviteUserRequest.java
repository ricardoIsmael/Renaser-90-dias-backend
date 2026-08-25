package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.users.api.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteUserRequest(
        @NotBlank String supabaseUserId,
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotNull UserRole role) {
}
