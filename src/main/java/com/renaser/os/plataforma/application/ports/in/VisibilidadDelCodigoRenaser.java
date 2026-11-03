package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.Map;

/**
 * Cómo está el interruptor «Código Renaser en las fichas» (spec 034): si el mentor, la guía y el líder lo ven.
 *
 * @param visible      los tres lo ven
 * @param mixto        no coinciden (Soporte TI los dejó distintos desde su consola); en ese caso {@code visible} es {@code false}
 * @param porRol       si cada uno de los tres lo ve
 * @param ultimoCambio quién lo cambió por última vez y cuándo, o {@code null} si nadie lo cambió (rige el valor de fábrica: apagado)
 */
public record VisibilidadDelCodigoRenaser(boolean visible, boolean mixto, Map<UserRole, Boolean> porRol,
                                          UltimoCambio ultimoCambio) {

    public VisibilidadDelCodigoRenaser {
        porRol = Map.copyOf(porRol);
    }

    /** @param porNombre el nombre de quien lo hizo, o {@code null} si la cuenta ya no existe */
    public record UltimoCambio(UserId porId, String porNombre, Instant en) {
    }
}
