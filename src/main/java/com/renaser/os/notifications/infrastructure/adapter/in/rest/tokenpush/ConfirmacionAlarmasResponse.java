package com.renaser.os.notifications.infrastructure.adapter.in.rest.tokenpush;

import java.time.Instant;

/** Cuando quedo anotada la confirmacion (reloj del servidor, no del telefono). */
public record ConfirmacionAlarmasResponse(Instant confirmadasEn) {
}
