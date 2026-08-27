package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetProgramDayRequest(@NotNull @Min(0) @Max(90) Integer programDay) {
}
