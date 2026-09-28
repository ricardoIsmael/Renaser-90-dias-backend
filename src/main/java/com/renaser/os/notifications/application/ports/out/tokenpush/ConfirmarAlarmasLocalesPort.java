package com.renaser.os.notifications.application.ports.out.tokenpush;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * Anota que el dispositivo de ese token tiene vivas sus alarmas locales de habitos (D-217). Puerto
 * propio y no un {@code upsert} mas: no crea tokens ni cambia de dueno, solo marca uno que ya es de
 * la persona.
 */
public interface ConfirmarAlarmasLocalesPort {

    /** @return cuantas filas se marcaron: 0 si el token no existe o es de otra persona. */
    int confirmar(UserId usuarioId, String token, Instant confirmadasEn);
}
