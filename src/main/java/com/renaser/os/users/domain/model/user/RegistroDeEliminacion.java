package com.renaser.os.users.domain.model.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.Objects;

/**
 * Una fila de la auditoria de eliminaciones (V90, D-243): quien cerro, recupero o elimino que cuenta,
 * cuando y desde donde. Sin datos personales a proposito —ni correo, ni nombre—: el dueño pidio que
 * quede auditado sin guardar lo borrado. Los ids son opacos una vez que la cuenta ya no existe.
 *
 * @param via     solo para {@link Accion#CERRADA}; {@code null} en las demas
 * @param actorId quien lo hizo; {@code null} cuando lo hace el barrido
 */
public record RegistroDeEliminacion(UserId cuentaId, UserRole rol, Accion accion, Via via, UserId actorId,
                                    Instant ocurridoEn) {

    public enum Accion { CERRADA, RECUPERADA, ELIMINADA_POR_ADMIN, ELIMINADA_AL_VENCER }

    /** Desde donde la persona cerro su cuenta. */
    public enum Via { APP, WEB }

    public RegistroDeEliminacion {
        Objects.requireNonNull(cuentaId, "cuentaId es obligatorio");
        Objects.requireNonNull(rol, "rol es obligatorio");
        Objects.requireNonNull(accion, "accion es obligatoria");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn es obligatorio");
        if ((via != null) != (accion == Accion.CERRADA)) {
            throw new IllegalArgumentException("La via solo va, y siempre va, cuando la cuenta se cierra");
        }
    }

    public static RegistroDeEliminacion cerrada(User cuenta, Via via, Instant ahora) {
        return new RegistroDeEliminacion(cuenta.id(), cuenta.role(), Accion.CERRADA, via, cuenta.id(), ahora);
    }

    public static RegistroDeEliminacion de(User cuenta, Accion accion, UserId actorId, Instant ahora) {
        return new RegistroDeEliminacion(cuenta.id(), cuenta.role(), accion, null, actorId, ahora);
    }
}
