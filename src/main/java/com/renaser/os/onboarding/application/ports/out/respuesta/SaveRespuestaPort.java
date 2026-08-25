package com.renaser.os.onboarding.application.ports.out.respuesta;

import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;

/**
 * {@link #guardar} es UPSERT por {@code (usuarioId, preguntaId)} (UNIQUE del baseline):
 * el adaptador busca una fila existente para esa clave y reutiliza su id antes de guardar,
 * asi guardar dos veces sobre la misma pregunta actualiza, nunca duplica.
 */
public interface SaveRespuestaPort {

    Respuesta guardar(Respuesta respuesta);
}
