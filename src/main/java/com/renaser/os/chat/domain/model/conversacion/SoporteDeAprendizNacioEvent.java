package com.renaser.os.chat.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Nació el chat de soporte de un aprendiz que acaba de entrar al programa (D-174).
 *
 * <p>Interno de {@code chat}: lo escucha la bienvenida automática. Solo lo publica la entrada de
 * un aprendiz nuevo, NUNCA el relleno de los que ya estaban, así nadie recibe una bienvenida
 * tarde por un barrido.
 */
public record SoporteDeAprendizNacioEvent(ConversacionId soporteId, UserId aprendizId) {
}
