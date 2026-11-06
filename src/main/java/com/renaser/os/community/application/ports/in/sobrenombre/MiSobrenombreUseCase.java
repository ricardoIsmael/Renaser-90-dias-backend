package com.renaser.os.community.application.ports.in.sobrenombre;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/** Spec 046 (D-331): el sobrenombre de la propia persona en el Muro. */
public interface MiSobrenombreUseCase {

    MiSobrenombre mio(UserId persona);

    /** Lo elige o lo cambia (una vez cada 30 días, D5). */
    MiSobrenombre elegir(UserId persona, String sobrenombre);

    /** Vuelve a su nombre en el Muro. */
    MiSobrenombre quitar(UserId persona);

    /** «Ahora no» a la invitación del día 7: no vuelve (D2). */
    MiSobrenombre descartarInvitacion(UserId persona);

    /**
     * @param puedeCambiarDesde desde cuándo puede volver a cambiarlo; {@code null} si ya puede
     */
    record MiSobrenombre(String sobrenombre, Instant puedeCambiarDesde, boolean invitacionDescartada) {
    }
}
