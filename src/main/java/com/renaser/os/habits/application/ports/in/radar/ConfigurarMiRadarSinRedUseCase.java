package com.renaser.os.habits.application.ports.in.radar;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.ConfigurarMiRadarCommand;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.shared.domain.HechoSinRed;

/**
 * Spec 048 (FR-019, fase 3): guardar Mi Radar lo que la persona hizo SIN RED. Mi Radar es una sola cosa (encendido y días se
 * guardan juntos): si en el servidor se cambió después de la hora en que lo hizo, gana lo del servidor.
 */
public interface ConfigurarMiRadarSinRedUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NoAplicadoSinRedException {@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR} (409) si
     *         {@code mi_radar_actualizado_en} es posterior a {@code hechoEn}
     */
    PlanDeMiRadar configurar(ConfigurarMiRadarCommand command, HechoSinRed sinRed);
}
