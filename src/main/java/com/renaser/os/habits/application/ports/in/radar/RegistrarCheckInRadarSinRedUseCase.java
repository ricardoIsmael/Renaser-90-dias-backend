package com.renaser.os.habits.application.ports.in.radar;

import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.HechoSinRed;

/**
 * Spec 048 (FR-019, fase 3): el Código Renaser que la persona respondió SIN RED, con la hora real en que lo hizo. Mismas reglas que
 * con red (día del programa, Mi Radar, horas de ese día, una por hora) corridas con esa hora; sin puntos. Un mismo
 * {@code idCliente} se registra una sola vez.
 */
public interface RegistrarCheckInRadarSinRedUseCase {

    /**
     * @return el registro, guardado con {@code creado_en} = la hora real (en el historial aparece a la hora en que se hizo)
     * @throws com.renaser.os.shared.domain.NoAplicadoSinRedException {@code MI_RADAR_NO_APLICA} (Mi Radar no tenía esa hora),
     *         {@code YA_ESTABA_HECHO} (esa hora ya tiene su registro: gana el del servidor) o
     *         {@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR} (Mi Radar se cambió después de lo que hiciste)
     */
    RegistroRadar registrar(RegistrarCheckInRadarCommand command, HechoSinRed sinRed);
}
