package com.renaser.os.support.infrastructure.adapter.in.rest.ticketmentor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResponderTicketMentorRequest(@NotBlank @Size(max = 4000) String mentorAnswer) {
}
