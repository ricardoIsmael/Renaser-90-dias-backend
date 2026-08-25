package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Sin campo role a proposito: CLAUDE.MD §5.3.3. */
public record SubmitAccountRequestRequest(
        @NotBlank String supabaseUserId,
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotBlank String phone,
        String city) {
}
