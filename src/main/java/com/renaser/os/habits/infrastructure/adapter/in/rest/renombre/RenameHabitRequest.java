package com.renaser.os.habits.infrastructure.adapter.in.rest.renombre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameHabitRequest(@NotBlank @Size(max = 60) String customTitle,
                                  @NotBlank @Size(max = 200) String reason) {
}
