package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

/**
 * Manda la bienvenida de Operaciones al chat de soporte recién nacido (D-174): la tarjeta de
 * Canva con el primer nombre, el mensaje que la acompaña y el mensaje formal (los textos y la portada
 * vigentes: los que guardó Administración desde la app, D-210, o los originales de
 * {@code bienvenida/mensajes.yaml} y {@code bienvenida/fondo.png}, D-190), firmados por el programa (D-199).
 *
 * <p>Apagada salvo {@code BIENVENIDA_ACTIVA=true} (D-199, 2026-09-27). Idempotente por destinatario
 * ({@code mensajes_bienvenida}, G-2): una reentrega no la repite. Si algo falla LANZA, para que el
 * outbox la reintente; el chat ya existe igual.
 * <blockquote><b>Corregido 2026-09-26 (G-2).</b> Decía «Nunca lanza: si algo falla, […] Operaciones
 * manda la bienvenida a mano». Sin marca, reintentar duplicaba; con la marca, tragarse el fallo solo
 * perdía la bienvenida.</blockquote>
 * <blockquote><b>Corregido 2026-09-27 (D-199).</b> La firmaba una cuenta de staff configurada
 * ({@code BIENVENIDA_REMITENTE_EMAIL}), y un remitente que no podía escribir en el soporte era
 * configuración inválida que se avisaba al arrancar (E-330, {@code revisarRemitenteConfigurado}).
 * Ahora la firma el programa: no hay remitente ni aviso.</blockquote>
 */
public interface DarBienvenidaEnSoporteUseCase {

    void darBienvenida(ConversacionId soporteId, UserId aprendizId);
}
