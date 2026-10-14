package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.domain.model.estado.ActorEvaluado;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

/**
 * Quien pide, para la evaluación: su id y su rol de hoy, leído recién cuando la evaluación lo necesita (una excepción
 * por rol en la cadena) y una sola vez por evaluación. Una cuenta sin rol conocido se evalúa como TRAINEE (contrato §5).
 */
@Component
class RolDelActor {

    private final UserSummaryFinder userSummaryFinder;

    RolDelActor(UserSummaryFinder userSummaryFinder) {
        this.userSummaryFinder = userSummaryFinder;
    }

    ActorEvaluado de(UserId actor) {
        return new ActorEvaluado() {
            private UserRole rol;

            @Override
            public UserId id() {
                return actor;
            }

            @Override
            public UserRole rol() {
                if (rol == null) {
                    rol = userSummaryFinder.findById(actor).map(UserSummary::role).orElse(UserRole.TRAINEE);
                }
                return rol;
            }
        };
    }
}
