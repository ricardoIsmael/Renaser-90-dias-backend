package com.renaser.os.calendar.application.ports.out.celula;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/** El mentor que lidera un grupo, para hacerlo coanfitrión de las salas de Meet de los eventos de ese grupo (spec 040 FR-038). */
@FunctionalInterface
public interface MentorDelGrupoPort {

    Optional<UserId> mentorDe(UUID celulaId);
}
