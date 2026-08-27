package com.renaser.os.onboarding.application.ports.in.respuesta;

import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Lectura de las respuestas YA guardadas del actor autenticado, agrupadas por seccion del
 * cuestionario — para "resumir/hidratar" un onboarding a medio terminar cuando el aprendiz
 * vuelve a entrar (antes de esto solo existia escritura, ver {@link GuardarRespuestaUseCase}).
 *
 * <p>Solo trae lo que el actor mismo respondio ({@code actorId} nunca viaja como parametro
 * de "usuario objetivo" — mismo diseño que {@code ObtenerCuestionarioUseCase}: este modulo
 * no tiene concepto de "actuar en nombre de otro", ver javadoc de {@code ConsultarActorPort}).
 * No hay alcance de mentor/admin documentado para leer respuestas ajenas — no se inventa.
 *
 * <p>Secciones sin ninguna pregunta respondida NO aparecen en el resultado (agrupar "de
 * forma util" implica no ensuciar la respuesta con secciones vacias); dentro de una seccion
 * incluida, solo se listan las preguntas que el actor ya respondio, nunca placeholders para
 * las que faltan (esa vista "que falta" ya la resuelve {@code GET /questionnaire} + diff en
 * el cliente, no es responsabilidad de este caso de uso).
 */
public interface ObtenerRespuestasUseCase {

    List<SeccionConRespuestas> obtener(ObtenerRespuestasQuery query);

    /** {@code flujo}: mismo query param {@code flow} que ya usa {@code GET /questionnaire}. */
    record ObtenerRespuestasQuery(@NotNull UserId actorId, @NotBlank String flujo) {

        public ObtenerRespuestasQuery {
            SelfValidating.validateConstructorArgs(ObtenerRespuestasQuery.class, actorId, flujo);
        }
    }

    record SeccionConRespuestas(Seccion seccion, List<PreguntaConRespuesta> preguntas) {
    }

    record PreguntaConRespuesta(Pregunta pregunta, Respuesta respuesta) {
    }
}
