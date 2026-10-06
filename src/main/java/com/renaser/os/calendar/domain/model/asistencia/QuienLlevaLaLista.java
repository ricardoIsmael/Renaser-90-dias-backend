package com.renaser.os.calendar.domain.model.asistencia;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.UserId;

import java.util.EnumSet;
import java.util.Set;

/**
 * Quién ve las respuestas de un evento y pasa lista (D-256, regla del dueño del 2026-10-06, literal):
 * «quien creó el evento + Admin, Alquimista y Líder de mentores» — en cualquier evento, también en uno que
 * el Admin creó para un mentor. Nadie más: ni un mentor que no lo creó, ni un aprendiz. La cuenta
 * suspendida se rechaza antes, al resolver al actor.
 *
 * <p>El aprendiz no ve su propia asistencia (regla del dueño): por eso esta regla no tiene un caso
 * «es sobre uno mismo».
 */
public final class QuienLlevaLaLista {

    private static final Set<RolUsuario> SIEMPRE = EnumSet.of(RolUsuario.ADMIN, RolUsuario.ALCHEMIST,
            RolUsuario.MENTOR_LEAD);

    private QuienLlevaLaLista() {
    }

    /** @param creadoPor quien creó el evento; {@code null} si esa cuenta se borró */
    public static boolean puede(RolUsuario rol, UserId actor, UserId creadoPor) {
        return SIEMPRE.contains(rol) || (creadoPor != null && creadoPor.equals(actor));
    }
}
