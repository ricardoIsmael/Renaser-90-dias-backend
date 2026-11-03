package com.renaser.os.onboarding.application.ports.out.ficha;

import com.renaser.os.shared.domain.UserId;

/** Lo que el servicio de la ficha necesita saber de las respuestas del diagnóstico de una persona. */
public interface RespuestasDelDiagnosticoPort {

    /** Si la persona respondió al menos una pregunta del flujo {@code diagnostico} (vigente o retirada). */
    boolean hayRespuestas(UserId persona);
}
