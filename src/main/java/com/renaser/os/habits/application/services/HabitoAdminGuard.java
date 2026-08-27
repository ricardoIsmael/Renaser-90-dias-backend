package com.renaser.os.habits.application.services;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * Guard compartido por los 3 servicios admin de este hueco (habito/horario/guia+adjuntos,
 * hueco #11): mismo criterio que {@code CategoriaMuroService}/{@code EvidenciaService}/etc.
 * en otros modulos (SUSPENDED -> 403, rol distinto de ADMIN/ALCHEMIST -> 403), pero
 * extraido a una sola clase DENTRO de {@code habits} en vez de duplicado 3 veces — a
 * diferencia de la duplicacion ENTRE modulos (que es deliberada, ver CLAUDE.MD §4.3:
 * modulos no comparten codigo de negocio), duplicar 3 veces en el mismo modulo no protege
 * ningun limite de Modulith, asi que aca si se extrae.
 */
@Component
class HabitoAdminGuard {

    private final UserSummaryFinder userSummaryFinder;

    HabitoAdminGuard(UserSummaryFinder userSummaryFinder) {
        this.userSummaryFinder = userSummaryFinder;
    }

    void requireAdmin(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran el catalogo de habitos");
        }
    }
}
