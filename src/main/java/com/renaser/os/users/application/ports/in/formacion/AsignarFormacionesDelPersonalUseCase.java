package com.renaser.os.users.application.ports.in.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/** Administración fija en qué formaciones trabaja un integrante del personal (spec 022, FR-007, FR-009, FR-011). */
public interface AsignarFormacionesDelPersonalUseCase {

    /**
     * @param activa la que queda como activa; {@code null} = conserva la que tenía si sigue asignada, o la primera
     * @throws IllegalArgumentException (400) si no hay ninguna formación o la activa no está entre ellas
     * @throws IllegalStateException    (409) si no es personal, o si se le quita una formación donde lidera un grupo (FR-011)
     */
    void asignar(AsignarFormacionesCommand command);

    record AsignarFormacionesCommand(UserId actor, UserId persona, Set<Formacion> formaciones, Formacion activa) {
    }
}
