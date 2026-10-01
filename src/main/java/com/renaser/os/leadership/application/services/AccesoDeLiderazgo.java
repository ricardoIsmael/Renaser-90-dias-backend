package com.renaser.os.leadership.application.services;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder;
import com.renaser.os.users.api.FichaDeMentorFinder.FichaDeMentor;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Quién puede gestionar el cuerpo de mentores: MENTOR_LEAD, ADMIN y ALCHEMIST, con la cuenta activa
 * (SDD 002, RL-26).
 *
 * <p><b>Por qué un guard aunque cada endpoint declare su permiso.</b> El interceptor deja pasar sin
 * mirar a MENTOR, ADMIN y ALCHEMIST (hueco A-1, {@code UserRole.can}) y a MENTOR_LEAD lo evalúa en modo
 * sombra: el permiso declarado no le cierra la puerta a un MENTOR. Mismo patrón que
 * {@code mentoring.AccesoAVistasDelSemaforo}.
 */
@Component
class AccesoDeLiderazgo {

    private static final Set<UserRole> LIDERAZGO = EnumSet.of(UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);

    private final UserSummaryFinder userSummaryFinder;
    private final FichaDeMentorFinder fichaDeMentorFinder;

    AccesoDeLiderazgo(UserSummaryFinder userSummaryFinder, FichaDeMentorFinder fichaDeMentorFinder) {
        this.userSummaryFinder = userSummaryFinder;
        this.fichaDeMentorFinder = fichaDeMentorFinder;
    }

    void requireLiderazgoActivo(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (!LIDERAZGO.contains(actor.role())) {
            throw new NotAuthorizedException("Solo MENTOR_LEAD/ADMIN/ALCHEMIST gestionan el cuerpo de mentores");
        }
    }

    /**
     * Va DESPUÉS del guard de rol: así un actor sin permiso no puede usar el endpoint para saber qué ids
     * son de mentores. Un id que no es de un mentor responde 404, no una ficha vacía (RL-08, RL-18).
     */
    FichaDeMentor requireMentor(UserId mentorId) {
        return fichaDeMentorFinder.mentor(mentorId)
                .orElseThrow(() -> new NoSuchElementException("Mentor no encontrado: " + mentorId));
    }
}
