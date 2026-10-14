package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Set;

/**
 * Cuáles cuentas son de prueba de «ver como rol» (V107, D-270), para lo que arma su lista con ids propios y no puede
 * filtrar en el SQL de {@code users}: los avisos (no se le manda nada a una cuenta de prueba), el ranking al leer, la
 * caja, el acceso del administrador a los chats de grupo. Una consulta indexada; las cuentas de prueba son a lo sumo
 * 6 + 20.
 */
public interface CuentasDePruebaFinder {

    boolean esCuentaDePrueba(UserId id);

    /** De estos ids, los de cuentas de prueba (vacío si ninguno). */
    Set<UserId> entre(Collection<UserId> ids);

    /** Todas las cuentas de prueba (a lo sumo 6 + 20): para restarlas de un conteo propio, como el panel de onboarding. */
    Set<UserId> todas();
}
