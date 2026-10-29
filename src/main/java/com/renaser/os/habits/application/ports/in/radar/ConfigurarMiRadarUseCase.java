package com.renaser.os.habits.application.ports.in.radar;

import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar.DiaPropio;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Enciende, apaga o cambia las horas de Mi Radar, para todos los dias y por dia de la semana (spec 012). Solo la propia
 * persona.
 */
public interface ConfigurarMiRadarUseCase {

    /**
     * @throws IllegalArgumentException si esta encendido y las horas o los dias no cumplen las reglas de
     *                                  {@link MiRadar}
     */
    MiRadar configurar(ConfigurarMiRadarCommand command);

    /**
     * Mismo patron que {@code RegistrarCheckInRadarCommand}: actor y participante separados para que el guard
     * {@code requireSelf} sea testeable. Con {@code encendido = false} las horas y los dias se ignoran; con
     * {@code horas} en {@code null} y encendido, el dominio responde "Elige al menos una hora". {@code dias} en
     * {@code null} equivale a {@code []}: todos los dias siguen {@code horas}.
     */
    record ConfigurarMiRadarCommand(@NotNull UserId actorId, @NotNull UserId participanteId,
                                    @NotNull Boolean encendido, List<Integer> horas, List<DiaPropio> dias) {

        public ConfigurarMiRadarCommand {
            dias = dias == null ? List.of() : dias;
            SelfValidating.validateConstructorArgs(ConfigurarMiRadarCommand.class, actorId, participanteId, encendido,
                    horas, dias);
        }
    }
}
