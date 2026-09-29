package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.chat.api.AvisosDeMensajesFinder;
import com.renaser.os.chat.api.AvisosDeMensajesFinder.AvisoDeMensaje;
import com.renaser.os.chat.api.AvisosDeMensajesFinder.Destinatario;
import com.renaser.os.chat.api.MensajeDeChatGuardadoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.chat.ContenidoDelMensaje;
import com.renaser.os.notifications.domain.model.chat.RedaccionDelMensajeDeChat;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Avisa con un push de cada mensaje nuevo de chat (D-221, pedido del dueño del 29/09: «que los chats
 * tengan notificación con el sonido tipo WhatsApp»). Hasta acá el tipo {@code MENSAJE_CHAT} existía y
 * nadie lo emitía: un mensaje no avisaba a nadie.
 *
 * <p>Quién recibe lo resuelve {@code chat} ({@link AvisosDeMensajesFinder}: participantes con la
 * regla de acceso de cada chat, menos el autor y menos quien lo tiene abierto). La preferencia
 * («Mensajes» en Yo → Notificaciones), la cuenta suspendida y el envío después del commit los pone el
 * camino de siempre, {@code NotificacionService.emitir}.
 *
 * <p><b>Idempotente ante un reintento del outbox:</b> el {@code origenEventoId} es el id del mensaje,
 * y el índice único de {@code notificaciones} es por (usuario, tipo, origen): cada persona recibe UN
 * aviso por mensaje aunque el evento se entregue dos veces.
 *
 * <p>La fila no se ve en la campana: la bandeja de avisos deja afuera {@code MENSAJE_CHAT} (el chat
 * tiene sus propios no leídos) y la purga la borra a los {@code Notificacion.RETENCION_MENSAJES_CHAT_DIAS}.
 */
@Component
class MensajeDeChatNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(MensajeDeChatNotificationListener.class);

    private final AvisosDeMensajesFinder avisosDeMensajes;
    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    MensajeDeChatNotificationListener(AvisosDeMensajesFinder avisosDeMensajes,
                                      EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.avisosDeMensajes = avisosDeMensajes;
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(MensajeDeChatGuardadoEvent event) {
        var aviso = avisosDeMensajes.avisoDe(event.mensajeId());
        if (aviso.isEmpty()) {
            log.debug("[notifications.MensajeDeChat] el mensaje {} ya no existe: no se avisa", event.mensajeId());
            return;
        }
        for (Destinatario destinatario : aviso.get().destinatarios()) {
            avisarA(aviso.get(), destinatario, event);
        }
    }

    /** Uno que falla no deja sin aviso a los demás (la comunidad son cientos). */
    private void avisarA(AvisoDeMensaje aviso, Destinatario destinatario, MensajeDeChatGuardadoEvent event) {
        try {
            var texto = RedaccionDelMensajeDeChat.redactar(destinatario.nombreDelChat(), destinatario.sinLeer(),
                    aviso.unoAUno(), aviso.autor(), ContenidoDelMensaje.valueOf(aviso.contenido().name()),
                    aviso.texto());
            emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(destinatario.usuarioId(),
                    TipoNotificacion.MENSAJE_CHAT, texto.titulo(), texto.cuerpo(), aviso.rutaApp(),
                    event.mensajeId()));
        } catch (RuntimeException e) {
            log.warn("[notifications.MensajeDeChat] no se pudo avisar a {} del mensaje {}: {}",
                    destinatario.usuarioId(), event.mensajeId(), e.getMessage());
        }
    }
}
