package com.renaser.os.habits.application.ports.in.radar;

import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * radar.ts:136-158 (`getLatestRadarEntryTime`) — la app solo necesita el
 * timestamp del ultimo check-in para calcular cuando se habilita el proximo
 * (gating por horario/slot, radar.ts:174-338). Se devuelve el registro
 * completo; el controller proyecta solo lo que la app usa.
 */
public interface ConsultarUltimoRadarUseCase {

    /** Autoservicio: actorId debe ser el propio participanteId (CLAUDE.MD §5.3.4, requireSelf). */
    Optional<RegistroRadar> ultimo(UserId actorId, UserId participanteId);
}
