package com.renaser.os.users.infrastructure.adapter.in.rest.mentorprofile;

import com.renaser.os.users.domain.model.mentorprofile.MentorOperationalStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Un solo campo, obligatorio: no hay "no cambiar" posible en una operacion que existe
 * unicamente para cambiarlo. A diferencia de {@link UpdateMentorProfileRequest}, que es un
 * parche con campos null.
 */
public record SetMentorOperationalStatusRequest(@NotNull MentorOperationalStatus status) {
}
