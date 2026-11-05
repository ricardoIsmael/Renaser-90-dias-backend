package com.renaser.os.calendar.application.ports.out.meet;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Lo único que Renaser le cuenta a Google de un evento (spec 040, FR-002 y FR-022): el id derivado, el título, cuándo empieza y termina y la
 * zona. Nunca nombres, correos, audiencia ni la descripción: ningún dato de personas viaja a Google.
 */
public record PedidoDeSala(String idGoogle, String titulo, Instant inicio, Instant fin, ZoneId zona) {
}
