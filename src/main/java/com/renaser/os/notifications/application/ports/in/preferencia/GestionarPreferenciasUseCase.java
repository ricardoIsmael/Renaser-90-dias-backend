package com.renaser.os.notifications.application.ports.in.preferencia;

import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Autoservicio estricto (CLAUDE.MD §0.3): los dos metodos solo reciben {@code actorId}, nunca
 * un id de usuario objetivo — no existe forma de que el actor toque las preferencias de otro,
 * la firma del comando lo hace imposible en vez de chequearlo en runtime (mismo principio que
 * el {@code role} ausente en {@code SubmitAccountRequestCommand} de `users`).
 */
public interface GestionarPreferenciasUseCase {

    /** Las {@code TipoNotificacion.values()} completas, con default HABILITADA para las que
     * no tienen fila propia — mismo criterio que {@code profile/service.ts:getNotificationPreferences}. */
    List<PreferenciaNotificacion> consultar(UserId actorId);

    List<PreferenciaNotificacion> actualizar(ActualizarPreferenciasCommand command);

    record ActualizarPreferenciasCommand(@NotNull UserId actorId,
                                          @NotEmpty List<@Valid ItemPreferencia> preferencias) {

        public ActualizarPreferenciasCommand {
            SelfValidating.validateConstructorArgs(ActualizarPreferenciasCommand.class, actorId, preferencias);
        }
    }

    record ItemPreferencia(@NotNull TipoNotificacion tipo, boolean habilitada) {

        public ItemPreferencia {
            SelfValidating.validateConstructorArgs(ItemPreferencia.class, tipo, habilitada);
        }
    }
}
