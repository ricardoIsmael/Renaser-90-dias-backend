package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EspecialidadMentor;

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

    /** TODOS los usuarios ACTIVOS con rol TRAINEE <b>inscritos en el programa</b>, tengan grupo
     * o no — {@code celulaActual} no nulo marca al que hoy ya pertenece a uno, igual que en
     * {@link #mentores}. Alcance GLOBAL, no por cohorte: un aprendiz sin celula no
     * tiene forma de saber a que cohorte "pertenece" todavia (esa relacion nace recien
     * cuando se lo asigna a una celula) — ver docs/MODULO_COMMUNITY.md, no se inventa una
     * columna de cohorte suelta que no existe en `participantes_programa`.
     *
     * <p><b>Corregido 2026-09-16 (E-190, D-137).</b> Aca decia "que hoy no son miembro de
     * ninguna celula". Esconder a los que ya tienen grupo dejaba el TRASLADO sin pantalla: el
     * dueno lo reporto como "no me deja agregar mas aprendices". Mover ya funcionaba en el
     * backend —{@code ComposicionDeCelulaService.asignar} cierra la pertenencia vigente y abre
     * la nueva, y ni siquiera cobra plaza cuando se reasigna dentro del mismo grupo—; lo unico
     * que faltaba era ofrecerlo. El filtro por inscripcion (E-186) NO se toca: sin el, el alta
     * responde 404.
     *
     * <p><b>Corregido 2026-09-15 (E-186).</b> Aca decia "Usuarios ACTIVOS con rol TRAINEE
     * que hoy no son miembro de ninguna celula", sin la inscripcion. El selector ofrecia
     * gente a la que {@code POST /admin/cells/&#123;id&#125;/trainees} responde 404 por no
     * tener fila en `participantes_programa`, y el admin veia "No se pudo agregar". */
    List<AprendizCandidato> aprendicesDisponibles(UserId actorId);

    /**
     * @param especialidad     NEGOCIO / MENTE / RELACIONES, o {@code null} si el mentor no la declaro.
     *                         El administrador elige el mentor del grupo POR esto (SDD 003, ARF-05),
     *                         asi que el selector la necesita; null se muestra "Sin especialidad
     *                         definida" y no se sustituye por ninguna de las tres.
     * @param celulasActuales  TODOS los grupos que el mentor lidera hoy, ordenados por nombre. Vacia
     *                         si no lidera ninguno. Existe desde D-141: un mentor puede liderar
     *                         varios a la vez, y hasta entonces esta respuesta solo podia nombrar
     *                         uno.
     * @param celulaActual     el primero de {@code celulasActuales}, o {@code null} si esta vacia.
     *                         Se conserva para los clientes que ya lo leen, pero cuando el mentor
     *                         lidera varios NO alcanza para decidir nada: preguntarle "¿lidera este
     *                         grupo?" da una respuesta equivocada en cuanto el que busca no es el
     *                         primero. Para eso esta {@code celulasActuales}.
     */
    record MentorCandidato(UserId userId, String nombreCompleto, String avatarUrl, CelulaId celulaActual,
                            List<CelulaId> celulasActuales, EspecialidadMentor especialidad) {
    }

    /**
     * @param celulaActual el grupo al que el aprendiz pertenece HOY, o {@code null} si no tiene
     *                     ninguno. Mismo papel que en {@link MentorCandidato}: elegirlo no es un
     *                     error sino un <b>traslado</b>, y la pantalla necesita poder decir de
     *                     donde lo saca. Se copia el patron del picker de mentores a proposito —
     *                     el contrato HTTP de los dos es el mismo campo `cellId`.
     */
    record AprendizCandidato(UserId userId, String nombreCompleto, String avatarUrl, CelulaId celulaActual) {
    }
}
