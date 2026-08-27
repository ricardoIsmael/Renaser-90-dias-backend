package com.renaser.os.onboarding.infrastructure.adapter.in.rest.respuesta;

import com.renaser.os.onboarding.application.ports.in.respuesta.ObtenerRespuestasUseCase.PreguntaConRespuesta;
import com.renaser.os.onboarding.application.ports.in.respuesta.ObtenerRespuestasUseCase.SeccionConRespuestas;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Respuestas ya guardadas del actor, agrupadas por seccion — para hidratar un onboarding a medio terminar. */
public record RespuestasAgrupadasResponse(String flow, List<SeccionResponse> sections) {

    public static RespuestasAgrupadasResponse from(String flow, List<SeccionConRespuestas> secciones) {
        return new RespuestasAgrupadasResponse(flow, secciones.stream().map(SeccionResponse::from).toList());
    }

    public record SeccionResponse(String sectionKey, String title, List<AnswerResponse> answers) {

        static SeccionResponse from(SeccionConRespuestas s) {
            Seccion seccion = s.seccion();
            return new SeccionResponse(seccion.claveSeccion(), seccion.titulo(),
                    s.preguntas().stream().map(AnswerResponse::from).toList());
        }
    }

    public record AnswerResponse(int questionId, String questionKey, TipoPreguntaOnboarding type, String textValue,
                                  BigDecimal numberValue, Boolean booleanValue, Short scaleValue, String jsonValue,
                                  Long mediaId, Instant acceptedAt, Instant answeredAt, Instant updatedAt) {

        static AnswerResponse from(PreguntaConRespuesta pr) {
            Pregunta pregunta = pr.pregunta();
            Respuesta r = pr.respuesta();
            return new AnswerResponse(pregunta.id(), pregunta.clavePregunta(), pregunta.tipo(), r.valorTexto(),
                    r.valorNumero(), r.valorBooleano(), r.valorEscala(), r.valorJson(), r.mediaId(), r.aceptadaEn(),
                    r.respondidaEn(), r.actualizadoEn());
        }
    }
}
