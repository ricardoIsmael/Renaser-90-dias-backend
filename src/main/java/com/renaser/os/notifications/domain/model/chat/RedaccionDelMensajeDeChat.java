package com.renaser.os.notifications.domain.model.chat;

/**
 * Título y cuerpo del aviso de un mensaje de chat (D-221), como los de WhatsApp.
 *
 * <ul>
 *   <li><b>Título:</b> el nombre del chat («Luisa y sus aprendices»); con dos o más sin leer,
 *       «Luisa y sus aprendices (3 mensajes nuevos)». En un 1 a 1 el chat se llama como quien escribe.</li>
 *   <li><b>Cuerpo:</b> «Pedro: hola». En un 1 a 1, solo «hola» (el nombre ya está en el título). Sin
 *       texto, qué es: «📷 Foto», «🎤 Nota de voz», «🎥 Video»; una foto con epígrafe, «📷 el epígrafe».</li>
 * </ul>
 *
 * <p>El aviso de un chat REEMPLAZA al anterior del mismo chat en la bandeja del teléfono (etiqueta por
 * conversación, {@code MensajePush.etiqueta}): por eso el conteo va en el título, para que el único
 * aviso que queda diga cuántos hay.
 *
 * <p>El texto se corta a {@link #LARGO_MAXIMO} caracteres: el push viaja con un tope de 4 KB entre
 * título, cuerpo y datos, y el teléfono muestra dos líneas.
 */
public final class RedaccionDelMensajeDeChat {

    static final int LARGO_MAXIMO = 140;

    private RedaccionDelMensajeDeChat() {
    }

    public record Aviso(String titulo, String cuerpo) {
    }

    /**
     * @param sinLeer cuántos lleva sin leer en ese chat, contando este (1 o más)
     * @param texto   {@code null} o en blanco si el mensaje no tiene texto
     */
    public static Aviso redactar(String nombreDelChat, long sinLeer, boolean unoAUno, String autor,
                                 ContenidoDelMensaje contenido, String texto) {
        String titulo = conConteo(nombreDelChat == null || nombreDelChat.isBlank() ? autor : nombreDelChat, sinLeer);
        String resumen = resumen(contenido, texto);
        return new Aviso(titulo, unoAUno ? resumen : autor + ": " + resumen);
    }

    private static String conConteo(String nombre, long sinLeer) {
        return sinLeer >= 2 ? nombre + " (" + sinLeer + " mensajes nuevos)" : nombre;
    }

    static String resumen(ContenidoDelMensaje contenido, String texto) {
        String limpio = texto == null ? "" : texto.strip().replaceAll("\\s+", " ");
        String prefijo = switch (contenido) {
            case TEXTO -> "";
            case FOTO -> "📷 ";
            case NOTA_DE_VOZ -> "🎤 ";
            case VIDEO -> "🎥 ";
        };
        if (limpio.isEmpty()) {
            return switch (contenido) {
                case TEXTO -> "Mensaje nuevo";
                case FOTO -> "📷 Foto";
                case NOTA_DE_VOZ -> "🎤 Nota de voz";
                case VIDEO -> "🎥 Video";
            };
        }
        return prefijo + cortar(limpio);
    }

    /** Corta por puntos de código, no por {@code char}: un emoji partido al medio se ve como «�». */
    private static String cortar(String texto) {
        if (texto.codePointCount(0, texto.length()) <= LARGO_MAXIMO) {
            return texto;
        }
        return texto.substring(0, texto.offsetByCodePoints(0, LARGO_MAXIMO - 1)).stripTrailing() + "…";
    }
}
