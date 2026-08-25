package com.renaser.os.habits.application.ports.in.santuario;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ExpirarRachasVencidasUseCase {

    /** Barrido: cierra las rachas ACTIVA de estos participantes que pasaron su plazo sin que
     * nadie las cerrara (sweepAllExpiredRuns). El scheduler resuelve la lista de participantes activos. */
    int expirarVencidas(List<UserId> participanteIds);
}
