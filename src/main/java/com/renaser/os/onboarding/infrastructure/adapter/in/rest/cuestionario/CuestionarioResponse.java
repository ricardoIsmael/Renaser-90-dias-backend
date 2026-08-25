package com.renaser.os.onboarding.infrastructure.adapter.in.rest.cuestionario;

import com.renaser.os.onboarding.application.ports.in.cuestionario.ObtenerCuestionarioUseCase.Cuestionario;
import com.renaser.os.onboarding.application.ports.in.cuestionario.ObtenerCuestionarioUseCase.PreguntaConOpciones;
import com.renaser.os.onboarding.application.ports.in.cuestionario.ObtenerCuestionarioUseCase.SeccionConPreguntas;
import com.renaser.os.onboarding.domain.model.cuestionario.OpcionPregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;

import java.util.List;

public record CuestionarioResponse(String flow, List<SeccionResponse> sections) {

    public static CuestionarioResponse from(Cuestionario c) {
        return new CuestionarioResponse(c.flujo(), c.secciones().stream().map(SeccionResponse::from).toList());
    }

    public record SeccionResponse(String sectionKey, String title, String description, short order,
                                   List<PreguntaResponse> questions) {

        static SeccionResponse from(SeccionConPreguntas s) {
            Seccion seccion = s.seccion();
            return new SeccionResponse(seccion.claveSeccion(), seccion.titulo(), seccion.descripcion(),
                    seccion.orden(), s.preguntas().stream().map(PreguntaResponse::from).toList());
        }
    }

    public record PreguntaResponse(int id, String questionKey, String text, TipoPreguntaOnboarding type,
                                    String scaleConfig, boolean required, short order, String validationRules,
                                    boolean conditional, Integer parentQuestionId, List<OpcionResponse> options) {

        static PreguntaResponse from(PreguntaConOpciones p) {
            Pregunta pregunta = p.pregunta();
            return new PreguntaResponse(pregunta.id(), pregunta.clavePregunta(), pregunta.texto(), pregunta.tipo(),
                    pregunta.configEscala(), pregunta.requerida(), pregunta.orden(), pregunta.reglasValidacion(),
                    pregunta.esCondicional(), pregunta.preguntaPadreId(),
                    p.opciones().stream().map(OpcionResponse::from).toList());
        }
    }

    public record OpcionResponse(short order, String value, String label) {

        static OpcionResponse from(OpcionPregunta o) {
            return new OpcionResponse(o.orden(), o.valor(), o.etiqueta());
        }
    }
}
