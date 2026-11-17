package com.renaser.os.notifications.application.ports.in.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;

/**
 * Deja como leídas las notificaciones sin leer que una más nueva SUPERA (issue #165): el aviso de hoy
 * dice lo mismo que el de ayer con la cifra al día, y los dos juntos en la bandeja se contradicen.
 *
 * <p>Marca como leída y no borra: la fila queda como historia (la purga a 90 días la limpia) y la
 * app ya oculta las leídas, así que no hace falta una marca nueva ni cambiar a quien lee la bandeja.
 */
public interface SuperarNotificacionesUseCase {

    /**
     * @param cuerpoComo patrón {@code LIKE} del cuerpo que identifica «lo mismo» dentro de la misma ruta
     *                   (por ejemplo, el mismo motivo para el mismo aprendiz).
     * @param idVigente  la notificación nueva: es la única que se conserva sin leer.
     * @return cuántas filas se superaron.
     */
    int superarAnteriores(UserId usuarioId, TipoNotificacion tipo, String rutaApp, String cuerpoComo,
                          Long idVigente);
}
