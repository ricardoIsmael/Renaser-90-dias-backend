package com.renaser.os.rag.application.ports.in.espejosombra;

import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Lista los informes del Espejo Sombra de UN participante. Misma regla de
 * visibilidad que {@link ObtenerInformeEspejoSombraUseCase} (D-47): {@code actorId}
 * debe ser el propio {@code participanteId}, su mentor asignado, o ADMIN/ALCHEMIST.
 */
public interface ListarInformesEspejoSombraUseCase {

    /** Más recientes primero. Lista vacía si el participante todavía no tiene ningún informe. */
    List<InformeEspejoSombra> deParticipante(UserId actorId, UserId participanteId);
}
