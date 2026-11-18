package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AprendicesDelAcompananteFinder;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
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
 * Quién pide el documento semanal de un aprendiz (spec 056, FR-041): el líder de su formación, Administración y Alquimista, y el mentor que lo
 * acompaña HOY. Cualquier otro recibe un 403 con su motivo. Va por relación y no por rol, como la ficha del mentor
 * ({@link AccesoDelMentorAlAprendiz}): el permiso declarado en el endpoint no protege nada (MENTOR pasa el interceptor sin que se le mire, A-1).
 *
 * <p>Al liderazgo se lo mira por su formación (spec 022): un aprendiz de otra formación se responde como si no existiera, un 404 sin pista, igual
 * que el resto de las lecturas del líder. El mentor que no acompaña a ese aprendiz recibe {@code NO_ACOMPANA}, sin decir si existe. Quien
 * acompaña hoy sale de las asignaciones vigentes ({@link AprendicesDelAcompananteFinder}), nunca de {@code participantes_programa.mentor_id}.
 */
@Component
class AccesoAlDocumento {

    private final UserSummaryFinder usuarios;
    private final AccesoDeLiderazgo liderazgo;
    private final PersonasDelPrograma programa;
    private final AprendicesDelAcompananteFinder acompanamiento;

    AccesoAlDocumento(UserSummaryFinder usuarios, AccesoDeLiderazgo liderazgo, PersonasDelPrograma programa,
                      AprendicesDelAcompananteFinder acompanamiento) {
        this.usuarios = usuarios;
        this.liderazgo = liderazgo;
        this.programa = programa;
        this.acompanamiento = acompanamiento;
    }

    /**
     * @return la persona y el grupo con que se la mira, ya autorizada la lectura
     * @throws NotAuthorizedException si quien pide no puede: {@code NO_ACOMPANA} si es un mentor que no lo acompaña
     * @throws java.util.NoSuchElementException (404) si la persona no existe, no se muestra o es de otra formación
     */
    Mirada paraElAprendiz(UserId actor, UUID personaId, Instant ahora) {
        UserSummary quien = usuarios.findById(actor).orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actor));
        if (quien.role().actuaComoMentor()) {
            return comoMentor(quien, UserId.of(personaId), ahora);
        }
        liderazgo.requireLiderazgoActivo(actor);
        return programa.mirarYDecirSiNoSigueSuPrograma(actor, UserId.of(personaId), ahora);
    }

    private Mirada comoMentor(UserSummary mentor, UserId aprendiz, Instant ahora) {
        if (mentor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida", NotAuthorizedException.CUENTA_SUSPENDIDA);
        }
        if (!acompanamiento.aprendicesQueAcompana(mentor.id(), ahora).contains(aprendiz)) {
            throw new NotAuthorizedException("Ese aprendiz no es de un grupo que acompañas.", AccesoDelMentorAlAprendiz.NO_ACOMPANA);
        }
        return programa.mirar(mentor.id(), aprendiz, ahora);
    }
}
