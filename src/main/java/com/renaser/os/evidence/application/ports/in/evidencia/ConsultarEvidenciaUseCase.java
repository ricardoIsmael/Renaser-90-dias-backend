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
     * backend. Por eso se emite recien cuando ya se sabe que quien la pide puede ver esa evidencia.
     *
     * <p><b>Corregido 2026-10-05 (D-252).</b> Decia: «Por eso no viaja en el listado —donde se
     * emitirian decenas por pantallazo, casi todas sin abrirse— sino solo cuando alguien pide ver
     * una en concreto». Cambio porque ahora el listado SI las muestra: Yo pinta la foto real de cada
     * evidencia en vez de un icono de camara (pedido del dueno). El listado firma solo las fotos de
     * su pagina (20 como mucho), despues de autorizar, y es calculo local del SDK (sin ida y vuelta a
     * S3), igual que el {@code mediaUrl} del chat. Este endpoint sigue para el VIDEO y el AUDIO, que
     * el listado no firma, y para quien abre una sola evidencia sin listar.
     *
     * <p>Vacio cuando la evidencia es de texto: no hay archivo que abrir.
     */
    Optional<UrlDeEvidencia> urlDeLectura(UserId actorId, EvidenciaId evidenciaId);

    /** @param venceEn cuando la URL deja de servir. Se informa para que el cliente no la cachee de mas. */
    record UrlDeEvidencia(String url, java.time.Instant venceEn) {
    }
}
