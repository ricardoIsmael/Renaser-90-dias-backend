package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

/**
 * Manda la bienvenida de Operaciones al chat de soporte recién nacido (D-174): la tarjeta de
 * Canva con el primer nombre y, si está configurado, el texto de bienvenida, firmados por la
 * cuenta de staff que configure el dueño.
 *
 * <p>Apagada mientras no haya remitente configurado. Nunca lanza: si algo falla, el chat ya
 * existe igual y Operaciones manda la bienvenida a mano como hasta hoy.
 */
public interface DarBienvenidaEnSoporteUseCase {

    void darBienvenida(ConversacionId soporteId, UserId aprendizId);
}
