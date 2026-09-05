package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/renasia/mensajes} (D-102: dos asistentes, un endpoint).
 *
 * <ul>
 *   <li>{@code agent}: {@code COMPANION} (acompanante de los 90 dias) o {@code COURSE_TUTOR}
 *   (Sparkie, tutor de cursos). Si falta, es {@code COMPANION}: un cliente anterior a D-102 sigue
 *   hablando con el acompanante sin cambiar nada. Cualquier otro valor es 400.</li>
 *   <li>{@code courseId}: solo para {@code COURSE_TUTOR}. Acota el contexto recuperado a las
 *   lecciones visibles de ese curso.</li>
 *   <li>{@code scope} (D-100): solo para {@code COURSE_TUTOR}. Sobre que esta hablando la
 *   persona — el cliente manda algo como {@code el curso "X", leccion "Y", dia 12 del programa}.
 *   Va al prompt de sistema; NUNCA se guarda como parte de {@code question}.</li>
 * </ul>
 *
 * <p>Para {@code COMPANION}, {@code courseId} y {@code scope} se ignoran (los descarta el
 * comando): el acompanante no tiene seccion de ambito en su prompt.
 */
public record PreguntarRenasiaRequest(@NotBlank String question,
                                      @Pattern(regexp = "COMPANION|COURSE_TUTOR",
                                               message = "agent debe ser COMPANION o COURSE_TUTOR") String agent,
                                      @Size(max = 120) String courseId,
                                      @Size(max = 300) String scope) {

    /** Sin {@code agent} = el acompanante (compatibilidad con clientes anteriores a D-102). */
    public AgenteConversacional agente() {
        return agent == null ? AgenteConversacional.COMPANION : AgenteConversacional.valueOf(agent);
    }
}
