package com.renaser.os.habits.application.ports.in.desbloqueo;

import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 *   <li>No hay un maximo de habitos elegibles: cualquier habito de catalogo activo se puede
 *       agregar.</li>
 * </ul>
 */
public interface ElegirHabitoUseCase {

    DesbloqueoHabito elegir(ElegirHabitoCommand command);

    /**
     * {@code diaDesbloqueo} nulo = arranca HOY (el dia de programa actual del aprendiz), que es
     * el comportamiento historico y el unico que existia antes. Con valor, el aprendiz elige
     * para que dia del programa quiere que empiece — "lo agrego ahora, pero lo empiezo el dia 2".
     *
     * <p>Solo hacia adelante: el dia pedido tiene que ser {@code >=} el dia actual del aprendiz
     * y {@code <= 90}. Desbloquear hacia atras no se acepta porque los dias ya vividos ya
     * generaron (o no) sus registros: fingir que el habito estaba ahi le mentiria a la
     * coherencia de esos dias.
     */
    record ElegirHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId,
                                @Min(1) @Max(90) Integer diaDesbloqueo) {
        public ElegirHabitoCommand {
            SelfValidating.validateConstructorArgs(ElegirHabitoCommand.class, actorId, habitoId, diaDesbloqueo);
        }
    }
}
