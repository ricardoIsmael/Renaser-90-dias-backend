package com.renaser.os.plataforma.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * El rol de hoy de quien pide, leído recién cuando la evaluación lo necesita (una excepción por rol en la cadena) y
 * una sola vez por evaluación. Una cuenta sin rol conocido se evalúa como TRAINEE (contrato §5).
 */
@Component
class RolDelActor {

    private final UserSummaryFinder userSummaryFinder;

    RolDelActor(UserSummaryFinder userSummaryFinder) {
        this.userSummaryFinder = userSummaryFinder;
    }

    Supplier<UserRole> de(UserId actor) {
        return new Supplier<>() {
            private UserRole rol;

            @Override
            public UserRole get() {
                if (rol == null) {
                    rol = userSummaryFinder.findById(actor).map(UserSummary::role).orElse(UserRole.TRAINEE);
                }
                return rol;
            }
        };
    }
}
