package com.renaser.os.shared;

import com.renaser.os.shared.domain.NotAuthorizedException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * Ayuda para las regresiones de E-169: comprobar que el guard de ROL ya no rechaza a quien tiene
 * su programa activado.
 *
 * <p><b>Por qué pide el mensaje exacto y no se conforma con "no lanzó nada".</b> Estas pruebas se
 * construyen sobre el fixture del caso de rechazo, que llega hasta el guard y no necesariamente
 * más allá: exigir un camino feliz completo obligaría a montar once escenarios enteros para
 * comprobar una condición de una línea. Pero tolerar <i>cualquier</i> excepción sería peor —
 * pasaría también si el servicio muriera antes de llegar al guard.
 *
 * <p>El punto medio es nombrar la puerta: falla solo si el mensaje es el del guard de rol, y deja
 * pasar el resto. Lo descubrió {@code RocaSemanalService}, que después del guard todavía exige
 * {@code ROCKS_LOCKED: completa tu onboarding antes de planificar rocas} — otra puerta, legítima,
 * que un helper más tosco confundía con un rechazo por rol.
 *
 * <p>Efecto secundario deseado: si alguien cambia el texto del guard, esto lo dice.
 */
public final class GuardDeRol {

    private GuardDeRol() {
    }

    /**
     * @param mensajeDelGuard el texto EXACTO con el que ese servicio rechaza por rol. Se pasa a
     *                        mano a propósito: obliga a mirar el guard que se está probando.
     */
    public static void noRechaza(ThrowingCallable llamada, String mensajeDelGuard) {
        try {
            llamada.call();
        } catch (NotAuthorizedException rechazo) {
            if (rechazo.getMessage() != null && rechazo.getMessage().contains(mensajeDelGuard)) {
                throw new AssertionError(
                        "el guard de rol rechazo a alguien con el programa activado: " + rechazo.getMessage(),
                        rechazo);
            }
            // Otra puerta distinta a la que esta prueba mide.
        } catch (Throwable otroFallo) {
            // Fixture incompleto: tampoco es lo que esta prueba mide.
        }
    }
}
