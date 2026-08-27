package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Sin campo role a proposito: CLAUDE.MD §5.3.3. Sin supabaseUserId desde 2026-08-27: el
 * backend genera el id internamente (D-49). */
public record SubmitAccountRequestRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotBlank String phone,
        String city) {
}
