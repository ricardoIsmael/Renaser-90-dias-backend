package com.renaser.os.chat.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que {@code notifications} necesita para avisar de un mensaje nuevo (D-221), resuelto por
 * {@code chat}, que es quien sabe quién está en cada conversación.
 *
 * <p>Los destinatarios ya vienen filtrados con las MISMAS reglas que deciden quién puede ver el chat:
 * nunca el autor; en un grupo, solo quien pertenece HOY (no la proyección de participantes, que
 * concede de más a quien rotó); en un soporte, el aprendiz y el staff que sigue siéndolo; y nadie que
 * tenga esa conversación abierta en vivo en este momento. Que la cuenta esté suspendida lo decide el
 * camino estándar del push ({@code NotificacionService}), igual que para cualquier otro aviso.
 */
public interface AvisosDeMensajesFinder {

    /** Vacío si el mensaje o su conversación ya no existen. */
    Optional<AvisoDeMensaje> avisoDe(UUID mensajeId);

    /**
     * @param rutaApp     adónde lleva tocarlo: {@code /chat/{conversacionId}}
     * @param unoAUno     si es un chat de dos: el título es el autor y el cuerpo va sin «Nombre:»
     * @param autor       quién lo escribió, con su nombre completo; «Formación Renaser» si es del programa
     * @param contenido   de qué es el mensaje
     * @param texto       el texto, o {@code null} si no tiene (una foto sin epígrafe, una nota de voz)
     */
    record AvisoDeMensaje(UUID conversacionId, String rutaApp, boolean unoAUno, String autor,
                          Contenido contenido, String texto, List<Destinatario> destinatarios) {
    }

    /**
     * @param nombreDelChat cómo se llama el chat PARA ESTA PERSONA (en un 1 a 1, el autor)
     * @param sinLeer       cuántos mensajes de ese chat lleva sin leer, contando este
     */
    record Destinatario(UserId usuarioId, String nombreDelChat, long sinLeer) {
    }

    enum Contenido { TEXTO, FOTO, NOTA_DE_VOZ, VIDEO }
}
