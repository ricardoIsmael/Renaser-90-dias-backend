package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.onboarding.application.ports.in.caja.CajaDelMentorUseCase;
import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * El chip de la caja en la ficha del aprendiz (D-219, spec §3): lo ve el Admin y el mentor que lo acompaña
 * HOY. «Hoy», no «alguna vez»: se pregunta a {@code community} por los grupos operativos en este instante
 * ({@link AcompanamientoFinder#acompanaVigente}), así un exmentor con la sesión todavía viva no pasa.
 */
@Service
public class CajaDelMentorService implements CajaDelMentorUseCase {

    private final GuardiaDeCaja guardia;
    private final CajasDelPadron padron;
    private final AcompanamientoFinder acompanamiento;
    private final Clock clock;

    CajaDelMentorService(GuardiaDeCaja guardia, CajasDelPadron padron, AcompanamientoFinder acompanamiento,
                         Clock clock) {
        this.guardia = guardia;
        this.padron = padron;
        this.acompanamiento = acompanamiento;
        this.clock = clock;
    }

    @Override
    public EstadoCaja estadoDe(UserId actorId, UserId aprendizId) {
        UserSummary actor = guardia.exigirActiva(actorId);
        if (actor.role() != UserRole.ADMIN && !loAcompanaHoy(actorId, aprendizId)) {
            throw new NotAuthorizedException("Solo su mentor ve la caja de este aprendiz");
        }
        return padron.deAprendiz(aprendizId).caja().estado();
    }

    private boolean loAcompanaHoy(UserId actorId, UserId aprendizId) {
        Instant ahora = clock.now();
        return acompanamiento.gruposOperativos(ahora).stream()
                .filter(grupo -> grupo.aprendices().contains(aprendizId))
                .anyMatch(grupo -> acompanamiento.acompanaVigente(actorId, grupo.grupoId(), ahora));
    }
}
