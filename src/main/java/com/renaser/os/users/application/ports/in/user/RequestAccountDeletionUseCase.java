package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Baja de cuenta autogestionada (gap #5, requisito de Google Play/Apple). Self-only por
 * diseño: el comando SOLO lleva {@code userId} del propio actor, nunca el de otro usuario -
 * nadie se da de baja en nombre de otro por esta via.
 *
 * <p>Idempotente: pedirla dos veces no reinicia el plazo de gracia (ver
 * {@code User.solicitarBaja}). NO borra nada de inmediato - marca
 * {@code usuarios.baja_solicitada_en} y un cron (ver {@code PurgeExpiredAccountsUseCase})
 * purga a los N dias configurados. El acceso se conserva durante la gracia a proposito: sin
 * el, no habria forma de arrepentirse y cancelar (ver {@code CancelAccountDeletionUseCase}).
 */
public interface RequestAccountDeletionUseCase {

    EstadoBajaCuenta request(RequestAccountDeletionCommand command);

    /**
     * {@code confirmacion} debe ser exactamente "ELIMINAR" (backend viejo,
     * PALABRA_DE_CONFIRMACION) - no sustituye a la reautenticacion (esa la hace el cliente
     * contra su propia sesion antes de llamar), pero impide que un request suelto o un
     * reintento automatico borren una cuenta sin intencion explicita.
     */
    record RequestAccountDeletionCommand(@NotNull UserId userId, @NotBlank String confirmacion) {

        public RequestAccountDeletionCommand {
            SelfValidating.validateConstructorArgs(RequestAccountDeletionCommand.class, userId, confirmacion);
        }
    }
}
