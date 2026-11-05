package com.renaser.os.calendar.infrastructure.adapter.out.persistence.persona;

import com.renaser.os.calendar.application.ports.out.persona.CorreosDePersonasPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CorreosDeUsuariosFinder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/** El correo de quien crea o edita un evento con Meet, por {@code users.api.CorreosDeUsuariosFinder} (spec 040, FR-035). */
@Component
class CorreosDesdeUsersAdapter implements CorreosDePersonasPort {

    private final CorreosDeUsuariosFinder correosFinder;

    CorreosDesdeUsersAdapter(CorreosDeUsuariosFinder correosFinder) {
        this.correosFinder = correosFinder;
    }

    @Override
    public Map<UserId, String> correosDe(Collection<UserId> ids) {
        return ids.isEmpty() ? Map.of() : correosFinder.correosDe(ids);
    }
}
