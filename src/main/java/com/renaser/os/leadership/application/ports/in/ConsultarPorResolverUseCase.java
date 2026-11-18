package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.domain.model.mentor.PeorEvaluacion.MentorEvaluado;
import com.renaser.os.shared.domain.UserId;

/**
 * Lo que necesita la atención del líder hoy, para la fila «Por resolver» de «Mis mentores» (spec 056, FR-002): los grupos en curso sin
 * mentor, las consultas sin responder y el mentor con la peor evaluación del mes. Solo cantidades y un nombre de mentor: ningún aprendiz con
 * nombre (decisión del dueño, 2026-10-01). Solo MENTOR_LEAD, ADMIN y ALCHEMIST con la cuenta activa, y de las formaciones que ven.
 */
public interface ConsultarPorResolverUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si quien pide no es del liderazgo o su cuenta está suspendida
     */
    PorResolver porResolver(UserId actorId);

    /**
     * Cada parte es null si su fuente no respondió: una fuente caída no vacía la respuesta ni se muestra como «nada que resolver» (RL-21).
     *
     * @param gruposSinMentor los grupos regulares en curso, de las formaciones del líder, que hoy no tienen mentor
     * @param consultas       las consultas abiertas de los aprendices de los grupos con mentor
     * @param peorMentor      el de evaluación más baja del mes si está por debajo del 60 %; null si ninguno lo está
     */
    record PorResolver(Integer gruposSinMentor, ConsultasSinResponder consultas, MentorEvaluado peorMentor) {
    }

    /** @param diasDeLaMasVieja días completos desde que se abrió la más antigua; null sin consultas */
    record ConsultasSinResponder(int cantidad, Integer diasDeLaMasVieja) {
    }
}
