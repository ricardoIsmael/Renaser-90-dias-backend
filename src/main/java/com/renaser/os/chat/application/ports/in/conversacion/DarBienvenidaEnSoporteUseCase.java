package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

/**
 * Manda la bienvenida de Operaciones al chat de soporte recién nacido (D-174): la tarjeta de
 * Canva con el primer nombre, el mensaje que la acompaña y el mensaje formal (textos en
 * {@code bienvenida/mensajes.yaml}, D-190), firmados por la cuenta de staff que configure el dueño.
 *
 * <p>Apagada mientras no haya remitente configurado. Idempotente por destinatario
 * ({@code mensajes_bienvenida}, G-2): una reentrega no la repite. Si algo falla LANZA, para que el
 * outbox la reintente; el chat ya existe igual.
 * <blockquote><b>Corregido 2026-09-26 (G-2).</b> Decía «Nunca lanza: si algo falla, […] Operaciones
 * manda la bienvenida a mano». Sin marca, reintentar duplicaba; con la marca, tragarse el fallo solo
 * perdía la bienvenida.</blockquote>
 *
 * <p>Un remitente que no puede escribir en el soporte (rol fuera de ADMIN/ALCHEMIST, o no
 * participante) es configuración inválida, no un fallo: no manda, no marca, no lanza, y avisa en el
 * log (E-330).
 */
public interface DarBienvenidaEnSoporteUseCase {

    void darBienvenida(ConversacionId soporteId, UserId aprendizId);

    /** Al arrancar: un {@code WARN} si el remitente configurado no puede firmar la bienvenida (E-330). */
    void revisarRemitenteConfigurado();
}
