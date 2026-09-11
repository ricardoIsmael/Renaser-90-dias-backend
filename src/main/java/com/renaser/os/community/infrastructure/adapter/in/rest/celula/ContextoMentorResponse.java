package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarContextoAcompanamientoUseCase.AsignacionResumen;
import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarContextoAcompanamientoUseCase.ContextoAcompanamiento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/v1/mentor/context}. Los nombres salen en inglés como el resto
 * de la superficie REST del proyecto, aunque el dominio esté en castellano.
 */
public record ContextoMentorResponse(PersonalProgram personalProgram, boolean canAccompany,
                                      Capabilities capabilities, List<Assignment> assignments) {

    public static ContextoMentorResponse from(ContextoAcompanamiento contexto) {
        return new ContextoMentorResponse(
                new PersonalProgram(contexto.participaEnPrograma(), contexto.diaDePrograma()),
                contexto.puedeAcompanar(),
                new Capabilities(contexto.capacidades().programaObligatorio(),
                        contexto.capacidades().puedeActivarPrograma(), contexto.capacidades().acompanar(),
                        contexto.capacidades().administrar()),
                contexto.asignaciones().stream().map(Assignment::from).toList());
    }

    /**
     * @param programRequired el cliente NO puede dejar pasar sin onboarding. Falso para el
     *                        staff: acompañar no exige cursar (D-07).
     * @param canAdminister   si la app muestra la entrada a Administracion (SDD 003, ARF-01). Es
     *                        una capacidad de INTERFAZ derivada de los guards reales, no un
     *                        permiso nuevo ni la matriz de {@code UserRole.can}. Quien autoriza
     *                        sigue siendo cada endpoint.
     */
    public record Capabilities(boolean programRequired, boolean canStartProgram, boolean canAccompany,
                                boolean canAdminister) {
    }

    /** {@code day} es null cuando no participa: no se manda 0, que significaría otra cosa. */
    public record PersonalProgram(boolean enrolled, Integer day) {
    }

    /**
     * @param coverage CON_MENTOR / SOPORTE / SIN_COBERTURA. Que no haya mentor no significa
     *                 que no haya grupo.
     * @param capacity null en recepción, que no tiene tope comercial (D-05).
     */
    public record Assignment(UUID groupId, String groupName, UUID cohortId, String type, String function,
                              Instant from, Instant to, String coverage, int learners, Integer capacity) {

        static Assignment from(AsignacionResumen resumen) {
            return new Assignment(resumen.grupoId(), resumen.grupoNombre(), resumen.cohorteId(),
                    resumen.tipo().name(), resumen.funcion().name(), resumen.desde(), resumen.hasta(),
                    resumen.cobertura().name(), resumen.aprendices(), resumen.cupo());
        }
    }
}
