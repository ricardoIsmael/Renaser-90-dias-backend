package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import jakarta.validation.constraints.NotBlank;

public record RejectAccountRequestRequest(@NotBlank String reason) {
}
