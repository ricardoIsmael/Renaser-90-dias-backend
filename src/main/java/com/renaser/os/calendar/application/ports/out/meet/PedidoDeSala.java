package com.renaser.os.calendar.application.ports.out.meet;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Lo único que Renaser le cuenta a Google de un evento (spec 040, FR-002 y FR-022): el id derivado, el título, cuándo empieza y termina y la
 * zona. Nunca la audiencia, la descripción ni los aprendices.
 *
 * <p>{@code invitados} (decisión 6 del dueño, 2026-11-05, FR-035): el correo de la persona de Renaser que crea o edita el evento (Administración o
 * Alquimista), que va como invitado del evento de Calendar —sin que Google le mande correos ({@code sendUpdates=none})— para que esa persona
 * figure en la reunión. {@code null} = no tocar los invitados que el evento de Google ya tenga (el proceso en segundo plano no sabe quién edita);
 * vacío = no invitar a nadie.
 */
public record PedidoDeSala(String idGoogle, String titulo, Instant inicio, Instant fin, ZoneId zona, List<String> invitados) {

    public PedidoDeSala {
        invitados = invitados == null ? null : List.copyOf(invitados);
    }

    /** Sin tocar los invitados. */
    public PedidoDeSala(String idGoogle, String titulo, Instant inicio, Instant fin, ZoneId zona) {
        this(idGoogle, titulo, inicio, fin, zona, null);
    }
}
