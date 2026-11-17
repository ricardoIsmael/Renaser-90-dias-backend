package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Quién abre la ficha de un aprendiz como MENTOR (spec 055, FR-017 y FR-028): quien acompaña hoy el grupo, con la cuenta
 * activa, y solo a los aprendices que hoy son de ese grupo. Es de RELACIÓN, no de rol: el permiso declarado en el endpoint no
 * protege nada (MENTOR pasa el interceptor sin que se le mire, A-1). Todo rechazo es el mismo 403 {@code NO_ACOMPANA}: no
 * confirma si el grupo o el aprendiz existen.
 */
@Component
class AccesoDelMentorAlAprendiz {

    static final String NO_ACOMPANA = "NO_ACOMPANA";

    private final AcompanamientoFinder acompanamiento;
    private final UserSummaryFinder usuarios;

    AccesoDelMentorAlAprendiz(AcompanamientoFinder acompanamiento, UserSummaryFinder usuarios) {
        this.acompanamiento = acompanamiento;
        this.usuarios = usuarios;
    }

    void exigir(UserId actorId, UUID grupoId, UserId aprendizId, Instant ahora) {
        if (!acompanamiento.acompanaVigente(actorId, grupoId, ahora)) {
            throw new NotAuthorizedException("No acompañas ese grupo.", NO_ACOMPANA);
        }
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida", NotAuthorizedException.CUENTA_SUSPENDIDA);
        }
        if (!acompanamiento.aprendicesVigentes(grupoId, ahora).contains(aprendizId)) {
            throw new NotAuthorizedException("Ese aprendiz ya no es de un grupo que acompañas.", NO_ACOMPANA);
        }
    }
}
