package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Pickers del panel admin de celulas (#25, docs/PLAN_INTEGRACION_FRONTEND.md sec. 5):
 * "que mentores/aprendices existen y no estan asignados" — antes documentado como fuera
 * de alcance (CM-3, docs/MODULO_COMMUNITY.md sec. 5) porque `users.api` solo exponia
 * lectura de un usuario a la vez. Ahora que {@code users.api.ParticipacionProgramaFinder}
 * expone {@code usuariosActivosConRol} EN LOTE, se puede construir sin N+1 ni importar
 * nada fuera de {@code users.api}.
 *
 * <p>Solo ADMIN/ALCHEMIST — mismo criterio que el resto del panel admin de celulas.
 */
public interface ConsultarCandidatosCelulaUseCase {

    /** Usuarios ACTIVOS con rol MENTOR que hoy NO lideran ninguna celula. */
    List<MentorCandidato> mentoresDisponibles(UserId actorId);

    /** TODOS los usuarios ACTIVOS con rol MENTOR — {@code celulaActual} no nulo si ya
     * lidera una (el picker los muestra a todos y marca quien ya esta ocupado, en vez de
     * ocultarlos). */
    List<MentorCandidato> mentores(UserId actorId);

    /** Usuarios ACTIVOS con rol TRAINEE que hoy no son miembro de ninguna celula.
     * Alcance GLOBAL, no por cohorte: un aprendiz sin celula no tiene forma de saber a
     * que cohorte "pertenece" todavia (esa relacion nace recien cuando se lo asigna a
     * una celula) — ver docs/MODULO_COMMUNITY.md, no se inventa una columna de cohorte
     * suelta que no existe en `participantes_programa`. */
    List<AprendizCandidato> aprendicesDisponibles(UserId actorId);

    record MentorCandidato(UserId userId, String nombreCompleto, String avatarUrl, CelulaId celulaActual) {
    }

    record AprendizCandidato(UserId userId, String nombreCompleto, String avatarUrl) {
    }
}
