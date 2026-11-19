package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarPorResolverUseCase.ConsultasSinResponder;
import com.renaser.os.leadership.application.ports.in.ConsultarPorResolverUseCase.PorResolver;
import com.renaser.os.leadership.domain.model.mentor.PeorEvaluacion.MentorEvaluado;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code GET /api/v1/leadership/mentors/por-resolver} (spec 056, FR-002): lo que el líder tiene que resolver hoy. Solo cantidades y el
 * nombre de un mentor: ningún aprendiz. Cada parte es null si su fuente no respondió (nunca un cero que parezca «nada que resolver»).
 *
 * @param gruposSinMentor los grupos en curso de sus formaciones que hoy no tienen mentor
 * @param consultas       las consultas sin responder de los aprendices de sus mentores
 * @param peorMentor      el mentor con la evaluación del mes más baja, si está por debajo del 60 %; null si ninguno
 */
public record PorResolverResponse(Integer gruposSinMentor, ConsultasResponse consultas, PeorMentorResponse peorMentor) {

    static PorResolverResponse from(PorResolver porResolver) {
        return new PorResolverResponse(porResolver.gruposSinMentor(), ConsultasResponse.from(porResolver.consultas()),
                PeorMentorResponse.from(porResolver.peorMentor()));
    }

    /** @param diasDeLaMasVieja días completos desde que se abrió la más antigua; null sin consultas */
    public record ConsultasResponse(int cantidad, Integer diasDeLaMasVieja) {
        static ConsultasResponse from(ConsultasSinResponder consultas) {
            return consultas == null ? null : new ConsultasResponse(consultas.cantidad(), consultas.diasDeLaMasVieja());
        }
    }

    /** @param evaluacion el porcentaje de su evaluación del mes */
    public record PeorMentorResponse(UUID id, String nombre, BigDecimal evaluacion) {
        static PeorMentorResponse from(MentorEvaluado mentor) {
            return mentor == null ? null : new PeorMentorResponse(mentor.id(), mentor.nombre(), mentor.porcentaje());
        }
    }
}
