package com.renaser.os.calendar.domain.model.evento.audiencia;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;

/**
 * Una persona de la lista fija de un evento (tabla {@code invitados_evento}): elegida por su nombre, del personal por su nombre, o que entró por su
 * color en la foto del semáforo (FR-010).
 *
 * @param color solo con {@link OrigenDelInvitado#COLOR}: el color que tenía en la foto
 */
public record Invitado(UserId persona, OrigenDelInvitado origen, ColorDeAudiencia color) {

    public Invitado {
        Objects.requireNonNull(persona, "persona es obligatoria");
        Objects.requireNonNull(origen, "origen es obligatorio");
        if ((origen == OrigenDelInvitado.COLOR) != (color != null)) {
            throw new IllegalArgumentException("El color va solo con el origen COLOR");
        }
    }
}
