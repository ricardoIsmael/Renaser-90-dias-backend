package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Set;

/**
 * Cuáles de unas cuentas son de prueba. Las cuentas de prueba nunca aparecen en las listas del líder (spec 005, FR-002
 * y SC-006), igual que las cerradas ({@link CuentasCerradasFinder}).
 *
 * <p><b>Hoy no existe la marca.</b> {@code usuarios.es_cuenta_de_prueba} (V107) vive solo en la fase 2 de Soporte TI
 * (R-1 de la spec 005): en esta base ninguna cuenta es de prueba, así que la única implementación responde «ninguna».
 * Al integrar la fase 2 se reemplaza por la que lee esa columna (T046) y nadie más cambia.
 */
public interface CuentasDePruebaFinder {

    /** De los ids dados, los que son de una cuenta de prueba. Vacío si ninguno. Una consulta, nunca una por id. */
    Set<UserId> dePruebaEntre(Collection<UserId> ids);

    default boolean esDePrueba(UserId id) {
        return dePruebaEntre(Set.of(id)).contains(id);
    }
}
