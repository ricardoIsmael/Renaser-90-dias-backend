package com.renaser.os.chat.domain.model.semaforo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Cuándo sale la tarjeta del semáforo de una persona (D-223): a las 23:50 de SU reloj, y de qué día es.
 *
 * <p><b>La ventana es 23:50–23:59 local.</b> El barrido corre cada 5 minutos y en cualquier zona el primero
 * que cae dentro manda la tarjeta; el de las 23:55 es el reintento si el de las 23:50 no corrió (backend
 * caído, un fallo). Pasada la medianoche, la de ese día ya no sale: una tarjeta de ayer a las 8 de la mañana
 * diría «Hoy llevas…» de un día que ya terminó, y el día nuevo tiene la suya a las 23:50.
 *
 * <p><b>El día es el de la persona, nunca el del servidor</b> (regla 02, E-91). A las 04:50 UTC el servidor
 * ya está en el día siguiente; en Lima son las 23:50 del anterior, y la tarjeta es de ese.
 */
public final class HoraDeLaTarjeta {

    public static final LocalTime DESDE = LocalTime.of(23, 50);

    private HoraDeLaTarjeta() {
    }

    /** @return el día local cuya tarjeta toca mandar en este instante, o vacío si no es la hora en esa zona */
    public static Optional<LocalDate> diaQueToca(Instant ahora, ZoneId zona) {
        ZonedDateTime local = ahora.atZone(zona);
        return local.toLocalTime().isBefore(DESDE) ? Optional.empty() : Optional.of(local.toLocalDate());
    }
}
