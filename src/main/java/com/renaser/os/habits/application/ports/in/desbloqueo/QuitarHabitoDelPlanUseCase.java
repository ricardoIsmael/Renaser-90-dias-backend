package com.renaser.os.habits.application.ports.in.desbloqueo;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Contraparte de {@link ElegirHabitoUseCase}: el aprendiz saca un habito de su plan (D-87).
 * Faltaba — se podia elegir pero no des-elegir, asi que un plan solo crecia.
 *
 * <p><b>Quitar no es lo mismo que pausar</b> ({@link CambiarEstadoHabitoDelPlanUseCase}):
 * pausar deja el habito en el plan, apagado y con la fecha en que se apago; quitar borra la
 * fila. Pausar es "esta semana no"; quitar es "esto no va conmigo".
 *
 * <p>Idempotente: quitar algo que no esta en el plan no falla (204 igual). Un habito
 * OBLIGATORIO no se puede quitar, por el mismo motivo que no se puede pausar.
 */
public interface QuitarHabitoDelPlanUseCase {

    void quitar(QuitarHabitoCommand command);

    record QuitarHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId) {
        public QuitarHabitoCommand {
            SelfValidating.validateConstructorArgs(QuitarHabitoCommand.class, actorId, habitoId);
        }
    }
}
