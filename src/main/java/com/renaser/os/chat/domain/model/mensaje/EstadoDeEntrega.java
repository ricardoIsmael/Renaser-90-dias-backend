package com.renaser.os.chat.domain.model.mensaje;

/**
 * La marca de un mensaje propio en el chat (D-208). En español en el dominio; el cable habla inglés
 * ({@code SENT}/{@code READ}, D-36) y la traducción vive solo en {@code MensajeResponse}.
 *
 * <p>No hay «entregado» (el ✓✓ gris de WhatsApp): el servidor no sabe cuándo un mensaje llegó a un
 * teléfono, solo cuándo alguien abrió la conversación. Prometerlo sería volver a lo que se corrigió el
 * 2026-09-26, cuando la app pintaba ✓✓ sin que nadie lo supiera.
 */
public enum EstadoDeEntrega {

    /** ✓: el servidor lo guardó. */
    ENVIADO,

    /** ✓✓ dorado: lo leyeron (en un grupo o un soporte, todos los demás). */
    LEIDO
}
