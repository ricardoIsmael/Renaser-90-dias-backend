package com.renaser.os.rag.application.ports.in.espejosombra;

import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombraId;
import com.renaser.os.shared.domain.UserId;

/**
 * Consulta un informe puntual del Espejo Sombra. Quién puede verlo (D-47,
 * docs/MODULO_RAG.md §3): el propio aprendiz, su mentor asignado, ADMIN o ALCHEMIST —
 * un tercero sin relación recibe 403, nunca 404 (no se filtra existencia).
 */
public interface ObtenerInformeEspejoSombraUseCase {

    InformeEspejoSombra porId(UserId actorId, InformeEspejoSombraId informeId);
}
