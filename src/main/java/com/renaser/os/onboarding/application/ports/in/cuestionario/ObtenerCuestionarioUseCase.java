package com.renaser.os.onboarding.application.ports.in.cuestionario;

import com.renaser.os.onboarding.domain.model.cuestionario.OpcionPregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface ObtenerCuestionarioUseCase {

    Cuestionario obtener(ObtenerCuestionarioQuery query);

    /** actorId solo se usa para exigir cuenta activa (§5.3.5) — el catalogo no es especifico de un usuario. */
    record ObtenerCuestionarioQuery(@NotNull UserId actorId, @NotBlank String flujo) {

        public ObtenerCuestionarioQuery {
            SelfValidating.validateConstructorArgs(ObtenerCuestionarioQuery.class, actorId, flujo);
        }
    }

    record Cuestionario(String flujo, List<SeccionConPreguntas> secciones) {
    }

    record SeccionConPreguntas(Seccion seccion, List<PreguntaConOpciones> preguntas) {
    }

    record PreguntaConOpciones(Pregunta pregunta, List<OpcionPregunta> opciones) {
    }
}
