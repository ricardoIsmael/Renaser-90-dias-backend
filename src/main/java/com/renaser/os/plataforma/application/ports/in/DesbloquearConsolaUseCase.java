package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Abre la consola Soporte TI con la contraseña, o confirma la contraseña para un cambio crítico (contrato §6,
 * {@code POST /soporte-ti/desbloqueo}).
 */
public interface DesbloquearConsolaUseCase {

    Desbloqueo desbloquear(DesbloquearConsolaCommand command);

    enum Para {
        /** Abre la consola por 15 minutos de inactividad. */
        CONSOLA,
        /** Un token de un solo uso, de 5 minutos, para un cambio crítico. Exige la consola ya desbloqueada. */
        CAMBIO_CRITICO
    }

    /** @param acceso con el token de la consola si {@code para} es {@code CAMBIO_CRITICO} */
    record DesbloquearConsolaCommand(AccesoDeConsola acceso, String contrasena, Para para, String dispositivo) {

        public DesbloquearConsolaCommand {
            Objects.requireNonNull(acceso, "acceso es obligatorio");
            if (contrasena == null || contrasena.isEmpty()) {
                throw new IllegalArgumentException("Escribe tu contraseña");
            }
            Objects.requireNonNull(para, "para es obligatorio");
        }

        public UserId actor() {
            return acceso.actor();
        }
    }

    record Desbloqueo(String token, Instant venceEn) {
    }
}
