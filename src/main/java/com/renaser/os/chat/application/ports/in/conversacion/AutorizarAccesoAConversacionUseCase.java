package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

/**
 * La unica respuesta del modulo a "¿esta persona puede ver esta conversacion AHORA?".
 *
 * <p><b>Por que existe (2026-09-18).</b> La regla estaba escrita cuatro veces: en
 * {@code MensajeService}, {@code ConversacionService}, {@code PresenciaService} y en el
 * interceptor de suscripciones del WebSocket. Las tres primeras se corrigieron juntas cuando se
 * vio que para un grupo no alcanza la proyeccion {@code participantes_conversacion}; la cuarta
 * quedo atras y nadie lo noto, porque su propio javadoc afirmaba que aplicaba "la MISMA regla".
 * Cuatro copias de una decision de autorizacion se desincronizan: esta es la unica.
 *
 * <p>La regla, en una linea: para una conversacion de <b>grupo</b> manda la pertenencia vigente
 * —el grupo tiene que estar operativo y la asignacion viva—, no la proyeccion. Para todo lo demas
 * (directa, soporte, global) la proyeccion <i>es</i> la fuente de verdad y alcanza.
 */
public interface AutorizarAccesoAConversacionUseCase {

    /**
     * {@code false} tambien cuando la conversacion no existe: quien pregunta no tiene por que
     * distinguir "no estas invitado" de "no existe", y fallar cerrado es lo correcto en los dos.
     */
    boolean puedeVer(ConversacionId conversacionId, UserId usuarioId);
}
