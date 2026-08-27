package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.TipoMedioGuia;

/**
 * Espejo de {@code HabitGuideAttachment} (`habitsAdmin.ts`). Para ENLACE, {@code url} es
 * la que pego el alquimista. Para IMAGEN/AUDIO esta respuesta TODAVIA devuelve cadena
 * vacia: {@link AdjuntoGuia} guarda {@code rutaStorage} cruda a proposito (invariante
 * "jamas una URL" para archivo, ver su javadoc) y resolverla a una URL de lectura firmada
 * en este listado admin (`GET .../guides`) queda fuera de este hueco — ver el reporte de
 * la tarea que agrego IMAGEN/AUDIO ({@code ConfirmarAdjuntoGuiaArchivoUseCase}). El propio
 * contrato del cliente ya define cadena vacia como "la fila existe pero su archivo no esta
 * disponible para previsualizar" — no es un dato incorrecto, es una previsualizacion que
 * todavia no esta cableada.
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
