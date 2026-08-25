package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.net.URI;

public interface SolicitarUrlPortadaUseCase {

    /**
     * Portada por {@code AlmacenamientoPort} (CLAUDE.MD §STORAGE) — URL prefirmada de
     * subida, no el multipart directo del repo viejo (uploadEventCover). Es una
     * adaptacion deliberada de infraestructura, no un cambio de regla de negocio: mismo
     * patron que {@code SolicitarUrlAdjuntoRocaUseCase} de `rocks`. El cliente sube el
     * archivo directo a la URL y despues confirma con {@link ConfirmarPortadaUseCase}.
     */
    UrlPortada solicitar(UserId actorId, EventoId eventoId, String tipoContenido);

    record UrlPortada(URI url, String bucket, String ruta) {
    }
}
