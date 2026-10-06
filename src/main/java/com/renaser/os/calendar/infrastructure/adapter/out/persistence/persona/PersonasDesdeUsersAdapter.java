package com.renaser.os.calendar.infrastructure.adapter.out.persistence.persona;

import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Nombre y foto por {@code users.api.UserSummaryFinder.findByIds}: una consulta para toda la lista (D-256). */
@Component
class PersonasDesdeUsersAdapter implements ConsultarPersonasPort {

    private final UserSummaryFinder userSummaryFinder;

    PersonasDesdeUsersAdapter(UserSummaryFinder userSummaryFinder) {
        this.userSummaryFinder = userSummaryFinder;
    }

    @Override
    public Map<UserId, Persona> porIds(Collection<UserId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userSummaryFinder.findByIds(ids).values().stream()
                .collect(Collectors.toMap(u -> u.id(), u -> new Persona(u.id(), u.fullName(), u.avatarUrl())));
    }
}
