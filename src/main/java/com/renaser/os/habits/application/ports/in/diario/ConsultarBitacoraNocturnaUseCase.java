package com.renaser.os.habits.application.ports.in.diario;

import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/** {@code GET /api/v1/journal/today} (repo viejo, R-05). {@code Optional.empty()} = todavia no escribio hoy. */
public interface ConsultarBitacoraNocturnaUseCase {

    Optional<EntradaDiario> consultarHoy(UserId actorId);
}
