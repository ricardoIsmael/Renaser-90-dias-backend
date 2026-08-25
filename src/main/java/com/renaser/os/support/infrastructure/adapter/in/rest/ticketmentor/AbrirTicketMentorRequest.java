package com.renaser.os.support.infrastructure.adapter.in.rest.ticketmentor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AbrirTicketMentorRequest(
        @NotBlank @Size(max = 2000) String blockDescription,
        @NotBlank @Size(max = 2000) String attemptedSolutions,
        @NotBlank @Size(max = 2000) String smartGoalImpact) {
}
