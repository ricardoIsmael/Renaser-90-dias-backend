package com.renaser.os.onboarding.application.ports.in.media;

import com.renaser.os.shared.domain.UserId;

import java.net.URI;
import java.time.Instant;

/**
 * La imagen de la firma del Pacto de la propia persona, para verla en Yo → Pacto ya firmado (D-253,
 * 2026-10-05, pedido del dueño). Hasta acá la firma se guardaba (PNG en S3, fila en
 * {@code medias_onboarding}, respuesta a la pregunta {@code signature} con su {@code mediaId}) pero ningún
 * endpoint la devolvía.
 *
 * <p><b>Solo la dueña.</b> No recibe a quién: es siempre la firma de quien pregunta, como el resto de este
 * módulo (no hay concepto de "actuar en nombre de otro", ver {@code ConsultarActorPort}). Ningún rol la veía
 * antes por otra vía, así que no se abre a ninguno.
 */
public interface VerFirmaDelPactoUseCase {

    /**
     * @throws java.util.NoSuchElementException si no hay firma del Pacto guardada (404)
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si la cuenta está suspendida, o si su
     *         respuesta apunta a un archivo de otra persona (403, ver E-528)
     */
    FirmaParaVer deActor(UserId actor);

    /**
     * @param url    URL de lectura firmada del PNG (válida 15 minutos, como el {@code mediaUrl} del chat)
     * @param venceEn hasta cuándo abre esa URL
     */
    record FirmaParaVer(URI url, Instant venceEn) {
    }
}
