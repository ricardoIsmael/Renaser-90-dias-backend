package com.renaser.os.habits.application.ports.in.desbloqueo;

import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Escritura sobre {@code desbloqueos_habito}: el aprendiz agrega un habito del catalogo a su
 * plan (hueco #12, cierra PARCIALMENTE D-H2 — ver javadoc de {@link ConsultarDesbloqueosHabitoUseCase}
 * para lo que sigue sin portarse). Esto NO es el algoritmo de escalonamiento por lotes
 * (dias 1/3/5/7, `habitStaggering.ts`) del repo viejo: es un alta autoservicio simple.
 *
 * <p><b>Simplificaciones deliberadas, NO confirmadas por negocio (ver
 * docs/informes/habits-eleccion-y-personales.md §4 para las preguntas abiertas):</b>
 * <ul>
 *   <li>{@code diaDesbloqueo} se fija al dia de programa ACTUAL del aprendiz en el momento de
 *       elegir (desbloqueo inmediato) — no hay escalonamiento por lotes.</li>
 *   <li>No hay un maximo de habitos elegibles: cualquier habito de catalogo activo se puede
 *       agregar.</li>
 * </ul>
 */
public interface ElegirHabitoUseCase {

    DesbloqueoHabito elegir(ElegirHabitoCommand command);

    record ElegirHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId) {
        public ElegirHabitoCommand {
            SelfValidating.validateConstructorArgs(ElegirHabitoCommand.class, actorId, habitoId);
        }
    }
}
