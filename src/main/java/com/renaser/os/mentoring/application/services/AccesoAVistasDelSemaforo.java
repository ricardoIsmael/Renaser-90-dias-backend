package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Quién puede mirar el semáforo de OTROS (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1.1): el
 * mentor, a los aprendices de los grupos que acompaña vigentemente; el líder, a los grupos sin
 * nombres; el administrador y el alquimista, todo.
 *
 * <p><b>Por qué un guard explícito aunque el endpoint declare un permiso.</b> El interceptor de
 * permisos deja pasar sin mirar a MENTOR, ADMIN y ALCHEMIST (hueco A-1, {@code UserRole.can}) y a
 * MENTOR_LEAD lo evalúa en modo sombra: para estas rutas el permiso declarado NO protege nada. Por
 * eso cada guard también exige cuenta ACTIVA — un mentor suspendido pasa el interceptor.
 *
 * <p>El guard del mentor es de RELACIÓN y los otros dos son de ROL; no se parametrizan entre sí
 * (ver {@code ConsultarSemaforoDelGrupoAdministrativoUseCase}).
 */
@Component
class AccesoAVistasDelSemaforo {

    private static final Set<UserRole> ADMINISTRACION = EnumSet.of(UserRole.ADMIN, UserRole.ALCHEMIST);
    private static final Set<UserRole> LIDERAZGO = EnumSet.of(UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);

    private final AcompanamientoFinder acompanamientoFinder;
    private final MedicionDeGrupos medicion;
    private final UserSummaryFinder userSummaryFinder;

    AccesoAVistasDelSemaforo(AcompanamientoFinder acompanamientoFinder, MedicionDeGrupos medicion,
                              UserSummaryFinder userSummaryFinder) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.medicion = medicion;
        this.userSummaryFinder = userSummaryFinder;
    }

    /** Acompaña el grupo HOY (un exmentor no pasa) y su cuenta está activa. */
    void requireAcompananteVigente(UserId actorId, UUID grupoId, Instant ahora) {
        if (!acompanamientoFinder.acompanaVigente(actorId, grupoId, ahora)) {
            // Mismo mensaje exista o no el grupo: distinguirlos confirmaria ids a quien los prueba.
            throw new NotAuthorizedException("No acompanas ese grupo");
        }
        requireCuentaActiva(actorId);
    }

    /** El aprendiz pedido es de ESE grupo, con el mismo padrón que muestra la tabla. */
    void requireAprendizDelGrupo(UserId aprendizId, UUID grupoId, Instant ahora) {
        if (!medicion.aprendicesDe(grupoId, ahora).contains(aprendizId)) {
            throw new NotAuthorizedException("Ese aprendiz no pertenece al grupo que acompanas");
        }
    }

    void requireAdminActivo(UserId actorId) {
        requireRolActivo(actorId, ADMINISTRACION, "Solo ADMIN/ALCHEMIST consultan el semaforo de cualquier grupo o persona");
    }

    void requireLiderazgoActivo(UserId actorId) {
        requireRolActivo(actorId, LIDERAZGO, "Solo MENTOR_LEAD/ADMIN/ALCHEMIST consultan el semaforo por grupos");
    }

    private void requireRolActivo(UserId actorId, Set<UserRole> roles, String motivo) {
        UserSummary actor = requireCuentaActiva(actorId);
        if (!roles.contains(actor.role())) {
            throw new NotAuthorizedException(motivo);
        }
    }

    private UserSummary requireCuentaActiva(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        return actor;
    }
}
