package com.renaser.os.evidence.application.ports.in.evidencia;

import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * Consulta una evidencia por id. Autoservicio con excepción admin: el dueño
 * ({@code participanteId == actorId}) siempre puede ver la suya; cualquier otro actor
 * necesita ser ADMIN/ALCHEMIST (CLAUDE.MD §0.3, "un actor no puede ver evidencia ajena
 * salvo admin").
 */
public interface ConsultarEvidenciaUseCase {

    Evidencia porId(UserId actorId, EvidenciaId evidenciaId);

    /**
     * URL temporal para VER el archivo de una evidencia.
     *
     * <p>Se firma recien despues de autorizar, nunca antes (plan.md §9): una URL prefirmada es
     * una llave que funciona sola: quien la tenga abre el archivo sin volver a pasar por el
     * backend. Por eso no viaja en el listado —donde se emitirian decenas por pantallazo, casi
     * todas sin abrirse— sino solo cuando alguien pide ver una en concreto.
     *
     * <p>Vacio cuando la evidencia es de texto: no hay archivo que abrir.
     */
    Optional<UrlDeEvidencia> urlDeLectura(UserId actorId, EvidenciaId evidenciaId);

    /** @param venceEn cuando la URL deja de servir. Se informa para que el cliente no la cachee de mas. */
    record UrlDeEvidencia(String url, java.time.Instant venceEn) {
    }
}
