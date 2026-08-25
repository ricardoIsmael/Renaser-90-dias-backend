package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignMentorRequest(@NotNull UUID mentorId) {
}
