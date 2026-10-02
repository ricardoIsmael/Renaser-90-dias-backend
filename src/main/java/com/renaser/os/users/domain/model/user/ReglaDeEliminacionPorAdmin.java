package com.renaser.os.users.domain.model.user;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.users.api.UserRole;

import java.util.Locale;
import java.util.Objects;

/**
 * Quien puede eliminar en el acto la cuenta de quien desde Administracion (decision 2 del dueño,
 * 2026-10-02, D-243). Es inmediato y definitivo —sin los 30 dias de gracia— y existe para borrar las
 * cuentas de prueba que quedaron en produccion.
 *
 * <ul>
 *   <li>Solo ADMIN y ALQUIMISTA, con la cuenta activa.</li>
 *   <li>A un ADMIN o a un ALQUIMISTA solo lo elimina un ADMIN.</li>
 *   <li>Nadie se elimina a si mismo por esta via (para eso esta «Eliminar mi cuenta», con gracia).</li>
 *   <li>Confirmacion fuerte: hay que escribir el correo de la cuenta.</li>
 * </ul>
 */
public final class ReglaDeEliminacionPorAdmin {

    private ReglaDeEliminacionPorAdmin() {
    }

    /** {@link NotAuthorizedException} (403) si el actor no puede eliminar esa cuenta. */
    public static void exigirPermitida(User actor, User cuenta) {
        Objects.requireNonNull(cuenta, "cuenta es obligatoria");
        if (actor == null || !actor.hasAccess() || !actor.canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST eliminan cuentas");
        }
        if (actor.id().equals(cuenta.id())) {
            throw new NotAuthorizedException("Nadie elimina su propia cuenta desde Administracion");
        }
        if (esDeGestion(cuenta.role()) && actor.role() != UserRole.ADMIN) {
            throw new NotAuthorizedException("Solo un ADMIN elimina la cuenta de un ADMIN o de un ALCHEMIST");
        }
    }

    /** {@link IllegalArgumentException} (400) si el correo escrito no es el de la cuenta. */
    public static void exigirCorreoConfirmado(User cuenta, String correoEscrito) {
        String escrito = correoEscrito == null ? "" : correoEscrito.trim().toLowerCase(Locale.ROOT);
        if (!escrito.equals(cuenta.email().value().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("CONFIRMACION_NO_COINCIDE: el correo escrito no es el de la cuenta");
        }
    }

    private static boolean esDeGestion(UserRole rol) {
        return rol == UserRole.ADMIN || rol == UserRole.ALCHEMIST;
    }
}
