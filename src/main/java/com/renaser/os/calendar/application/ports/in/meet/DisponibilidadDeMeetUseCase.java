package com.renaser.os.calendar.application.ports.in.meet;

import com.renaser.os.shared.domain.UserId;

public interface DisponibilidadDeMeetUseCase {

    /** Solo ADMIN y ALCHEMIST; cualquier otro, 403. */
    DisponibilidadDeMeet consultar(UserId actor);
}
