package com.renaser.os.chat.application.services;

import com.renaser.os.users.api.UserRole;

import java.util.EnumSet;
import java.util.Set;

/**
 * Quién acompaña el chat de soporte de un aprendiz, además del aprendiz (D-136, corregido por la spec 044).
 *
 * <p>Administración y Alquimista, todos los soportes. <b>Desde la spec 044 (adicional 11 del dueño, 2026-11-06), también el líder de mentores</b>, en
 * los soportes de los aprendices de su formación: la formación la controla {@code VisibilidadPorFormacion} antes de esta pregunta en las cuatro
 * copias de la regla de acceso, y al sumarlo como participante ({@code ConversacionSoporteService}). El mentor sigue sin estar.
 *
 * <blockquote><b>Corregido 2026-11-06 (spec 044).</b> Era solo {@code canManageRoles()} (ADMIN y ALCHEMIST), con la nota «ni mentor, ni líder de
 * mentores». El dueño pidió: «en el grupo de soporte también está el líder de mentores».</blockquote>
 */
final class StaffDelSoporte {

    /** Los roles que acompañan los soportes: ADMIN y ALCHEMIST todos; MENTOR_LEAD los de su formación. */
    static final Set<UserRole> ROLES = Set.copyOf(EnumSet.of(UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.MENTOR_LEAD));

    private StaffDelSoporte() {
    }

    static boolean acompana(UserRole rol) {
        return rol != null && ROLES.contains(rol);
    }

    /** Solo el líder acompaña acotado a su formación; los demás, todos los soportes. */
    static boolean acotadoASuFormacion(UserRole rol) {
        return rol == UserRole.MENTOR_LEAD;
    }
}
