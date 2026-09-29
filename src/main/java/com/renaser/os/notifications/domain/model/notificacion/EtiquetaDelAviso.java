package com.renaser.os.notifications.domain.model.notificacion;

import java.util.Optional;

/**
 * Qué avisos se REEMPLAZAN unos a otros en el teléfono en vez de apilarse (D-221): los de un mismo
 * chat, como WhatsApp. La comunidad son cientos de personas escribiendo; sin esto, cada mensaje sería
 * un aviso más en la bandeja del sistema.
 *
 * <p>La etiqueta va en el push como {@code tag} (Android: el aviso con la misma etiqueta reemplaza al
 * que está a la vista), {@code threadId} (iOS: los agrupa) y {@code tag} del navegador. Se deriva de
 * la ruta, que ya identifica el chat ({@code /chat/{id}} → {@code chat-{id}}): así no hace falta
 * sumar un campo al comando de emisión que usan los veintitantos listeners de siempre.
 */
public final class EtiquetaDelAviso {

    private static final String RUTA_DE_CHAT = "/chat/";

    private EtiquetaDelAviso() {
    }

    /** Vacío = cada aviso es uno aparte, como hasta ahora. */
    public static Optional<String> de(TipoNotificacion tipo, String rutaApp) {
        if (tipo != TipoNotificacion.MENSAJE_CHAT || rutaApp == null || !rutaApp.startsWith(RUTA_DE_CHAT)
                || rutaApp.length() == RUTA_DE_CHAT.length()) {
            return Optional.empty();
        }
        return Optional.of("chat-" + rutaApp.substring(RUTA_DE_CHAT.length()));
    }
}
