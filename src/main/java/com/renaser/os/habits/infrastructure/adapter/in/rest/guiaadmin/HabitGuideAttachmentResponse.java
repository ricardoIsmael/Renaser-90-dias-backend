package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.TipoMedioGuia;

/**
 * Espejo de {@code HabitGuideAttachment} (`habitsAdmin.ts`). Para ENLACE, {@code url} es
 * la que pego el alquimista. Para IMAGEN/AUDIO NO se firma una URL de lectura en esta
 * pasada — esta build solo crea adjuntos ENLACE (ver {@code CrearAdjuntoGuiaEnlaceUseCase}
 * javadoc); si algun dia existe una fila IMAGEN/AUDIO (cargada por otro camino), esta
 * respuesta devuelve cadena vacia, que el propio contrato del cliente ya define como
 * "la fila existe pero su archivo no esta disponible" — no es un dato incorrecto.
 */
public record HabitGuideAttachmentResponse(String id, HabitGuideSectionDto section, String url, String title,
                                            int displayOrder, HabitGuideAttachmentMediaTypeDto mediaType,
                                            String mimeType, Integer sizeBytes, String originalName) {

    public static HabitGuideAttachmentResponse from(AdjuntoGuia adjunto) {
        String url = adjunto.tipoMedio() == TipoMedioGuia.ENLACE ? adjunto.url() : "";
        return new HabitGuideAttachmentResponse(adjunto.id().value().toString(),
                HabitGuideSectionDto.from(adjunto.seccion()), url, adjunto.titulo(), adjunto.orden(),
                HabitGuideAttachmentMediaTypeDto.from(adjunto.tipoMedio()), adjunto.mime(), adjunto.tamanoBytes(),
                adjunto.nombreOriginal());
    }
}
