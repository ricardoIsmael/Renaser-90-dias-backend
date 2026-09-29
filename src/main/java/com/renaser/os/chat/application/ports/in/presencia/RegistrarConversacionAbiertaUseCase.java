package com.renaser.os.chat.application.ports.in.presencia;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

/**
 * Lo que el adaptador de WebSocket le cuenta a la aplicación sobre qué chat tiene abierto cada
 * persona (D-221): así no se le manda un push de un mensaje que ya está viendo, como WhatsApp.
 *
 * <p>La app se suscribe a {@code /topic/conversaciones/{id}} SOLO mientras esa conversación está en
 * pantalla y la app en primer plano (al pasar a segundo plano se desuscribe), así que «suscripto» es
 * «la tiene abierta». Como en la presencia, el adaptador cuenta las suscripciones (primera abre,
 * última cierra) porque es el único que ve los sockets.
 */
public interface RegistrarConversacionAbiertaUseCase {

    void laAbrio(UserId usuarioId, ConversacionId conversacionId);

    void laCerro(UserId usuarioId, ConversacionId conversacionId);

    /** Renueva el vencimiento mientras siga abierta. Una instancia que muere deja de renovar y vence sola. */
    void sigueAbierta(UserId usuarioId, ConversacionId conversacionId);
}
