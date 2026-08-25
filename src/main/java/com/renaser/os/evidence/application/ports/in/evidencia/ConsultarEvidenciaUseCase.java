package com.renaser.os.evidence.application.ports.in.evidencia;

import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.UserId;

/**
 * Consulta una evidencia por id. Autoservicio con excepción admin: el dueño
 * ({@code participanteId == actorId}) siempre puede ver la suya; cualquier otro actor
 * necesita ser ADMIN/ALCHEMIST (CLAUDE.MD §0.3, "un actor no puede ver evidencia ajena
 * salvo admin").
 */
public interface ConsultarEvidenciaUseCase {

    Evidencia porId(UserId actorId, EvidenciaId evidenciaId);
}
