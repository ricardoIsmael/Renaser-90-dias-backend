package com.renaser.os.habits.application.ports.in.desbloqueo;

import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * El interruptor ACTIVO/PAUSADO de la pantalla Plan (D-87). Autoservicio del propio aprendiz:
 * el comando NO recibe un id ajeno, asi que es self por construccion.
 *
 * <p><b>No confundir con {@code CambiarActivoHabitoUseCase}</b> del panel admin: ese escribe
 * {@code habitos.activo}, que es del catalogo COMPARTIDO y afecta a todos los aprendices a la
 * vez. Este escribe {@code desbloqueos_habito.pausado_en}, que es solo de quien lo pide. La
 * confusion entre los dos es justamente lo que dejo el boton de Plan sin backend durante meses
 * (ver docs/informes/habits-eleccion-y-personales.md §0).
 *
 * <p>Un habito OBLIGATORIO ({@code habitos.desactivable = false}, V18) no se puede pausar:
 * devuelve 409, no 403 — no es un problema de permisos, es que la operacion no aplica a ese
 * habito para nadie.
 */
public interface CambiarEstadoHabitoDelPlanUseCase {

    DesbloqueoHabito cambiarEstado(CambiarEstadoHabitoCommand command);

    /** @param activo {@code true} reactiva, {@code false} pausa. */
    record CambiarEstadoHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId, boolean activo) {
        public CambiarEstadoHabitoCommand {
            SelfValidating.validateConstructorArgs(CambiarEstadoHabitoCommand.class, actorId, habitoId, activo);
        }
    }
}
