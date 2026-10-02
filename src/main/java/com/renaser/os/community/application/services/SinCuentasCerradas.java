package com.renaser.os.community.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Saca de un listado lo de las cuentas cerradas esperando su borrado definitivo (D-243): durante los 30
 * días de gracia sus filas siguen en la base —si no, recuperar la cuenta no tendría sentido—, pero los
 * demás ya no la ven en grupos, Muro ni testimonios.
 *
 * <p>Una consulta por listado ({@link CuentasCerradasFinder#cerradasEntre}), nunca una por fila. Las
 * suspensiones a secas no cambian nada: el finder responde solo por las cerradas para eliminar. Las
 * pantallas de Administración no pasan por acá y siguen viéndolo todo.
 */
final class SinCuentasCerradas {

    private final CuentasCerradasFinder cuentasCerradas;

    SinCuentasCerradas(CuentasCerradasFinder cuentasCerradas) {
        this.cuentasCerradas = cuentasCerradas;
    }

    /** Las filas cuya persona no está cerrada, en el mismo orden. */
    <T> List<T> de(List<T> filas, Function<T, UserId> persona) {
        if (filas.isEmpty()) {
            return filas;
        }
        Set<UserId> cerradas = cerradasEntre(filas, persona);
        if (cerradas.isEmpty()) {
            return filas;
        }
        return filas.stream().filter(fila -> !cerradas.contains(persona.apply(fila))).toList();
    }

    /** Las personas cerradas entre las de estas filas (las filas sin persona no cuentan). */
    <T> Set<UserId> cerradasEntre(List<T> filas, Function<T, UserId> persona) {
        if (filas.isEmpty()) {
            return Set.of();
        }
        return cuentasCerradas.cerradasEntre(filas.stream().map(persona).filter(Objects::nonNull).distinct().toList());
    }
}
