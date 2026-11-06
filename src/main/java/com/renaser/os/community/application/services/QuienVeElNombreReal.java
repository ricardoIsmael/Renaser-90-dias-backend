package com.renaser.os.community.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Spec 046 (D-331): quién ve, en el Muro, el nombre real de alguien que tiene sobrenombre. El equipo que acompaña —mentor, guía de
 * aprendiz, líder de mentores, Administración, Alquimista y Soporte TI—; los aprendices solo ven el sobrenombre.
 *
 * <p>Simplificación aceptada: un mentor ve el nombre real de cualquier persona de su formación con sobrenombre, no solo de su grupo
 * (antes del sobrenombre ya veía el nombre de todos en el Muro).
 */
@Component
public class QuienVeElNombreReal {

    static final Set<UserRole> EL_EQUIPO = EnumSet.of(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD, UserRole.ADMIN,
            UserRole.ALCHEMIST, UserRole.IT_SUPPORT);

    private final UserSummaryFinder usuarios;

    public QuienVeElNombreReal(UserSummaryFinder usuarios) {
        this.usuarios = usuarios;
    }

    public boolean ve(UserId quienMira) {
        return quienMira != null && usuarios.findById(quienMira).map(u -> EL_EQUIPO.contains(u.role())).orElse(false);
    }
}
