package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Set;

/**
 * Cuáles cuentas son de prueba de «ver como rol» (V117, D-273), para lo que arma su lista con ids propios y no puede
 * filtrar en el SQL de {@code users}: los avisos (no se le manda nada a una cuenta de prueba), el ranking al leer, la
 * caja, el acceso del administrador a los chats de grupo y las listas del líder (spec 005, FR-002 y SC-006, igual que
 * las cerradas de {@link CuentasCerradasFinder}). Una consulta indexada; las cuentas de prueba son a lo sumo 6 + 20.
 */
public interface CuentasDePruebaFinder {

    boolean esCuentaDePrueba(UserId id);

    /** De estos ids, los de cuentas de prueba (vacío si ninguno). */
    Set<UserId> entre(Collection<UserId> ids);

    /** Todas las cuentas de prueba (a lo sumo 6 + 20): para restarlas de un conteo propio, como el panel de onboarding. */
    Set<UserId> todas();

    /** Nombre con que la spec 005 lo pide: lo mismo que {@link #entre}. Una consulta, nunca una por id. */
    default Set<UserId> dePruebaEntre(Collection<UserId> ids) {
        return entre(ids);
    }

    /** Nombre con que la spec 005 lo pide: lo mismo que {@link #esCuentaDePrueba}. */
    default boolean esDePrueba(UserId id) {
        return esCuentaDePrueba(id);
    }
}
