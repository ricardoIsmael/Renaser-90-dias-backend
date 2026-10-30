package com.renaser.os.habits.application.ports.in.radar;

import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Enciende, apaga o cambia Mi Radar por dia de la semana (spec 019). Solo la propia persona.
 */
public interface ConfigurarMiRadarUseCase {

    /**
     * @throws IllegalArgumentException si un dia, un modo o unas veces no cumplen las reglas, o las veces no caben en el
     *                                  horario de ese dia (400)
     * @throws IllegalStateException    si el pedido cambia hoy o un dia pasado (409)
     */
    PlanDeMiRadar configurar(ConfigurarMiRadarCommand command);

    /**
     * Mismo patron que {@code RegistrarCheckInRadarCommand}: actor y participante separados para que el guard
     * {@code requireSelf} sea testeable. {@code dias} son SOLO los dias que se tocan (null equivale a []): lo que no llega se
     * queda como estaba.
     */
    record ConfigurarMiRadarCommand(@NotNull UserId actorId, @NotNull UserId participanteId,
                                    @NotNull Boolean encendido, List<CambioDeDia> dias) {

        public ConfigurarMiRadarCommand {
            dias = dias == null ? List.of() : List.copyOf(dias);
            SelfValidating.validateConstructorArgs(ConfigurarMiRadarCommand.class, actorId, participanteId, encendido,
                    dias);
        }
    }

    /** Lo que la persona toco de un dia: lo que viene en {@code null} no se cambia. */
    record CambioDeDia(int dia, Modo modo, Integer veces, Boolean sinRadar) {
    }
}
