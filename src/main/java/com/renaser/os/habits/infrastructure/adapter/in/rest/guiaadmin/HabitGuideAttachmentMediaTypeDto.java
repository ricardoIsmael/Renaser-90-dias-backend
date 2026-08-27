package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.domain.model.guia.TipoMedioGuia;

/** Espejo de {@code HabitGuideAttachmentMediaType} (`habitsAdmin.ts`). */
public enum HabitGuideAttachmentMediaTypeDto {
    LINK,
    IMAGE,
    AUDIO;

    public static HabitGuideAttachmentMediaTypeDto from(TipoMedioGuia tipo) {
        return switch (tipo) {
            case ENLACE -> LINK;
            case IMAGEN -> IMAGE;
            case AUDIO -> AUDIO;
        };
    }

    /** Usado por la confirmacion de adjunto por archivo — LINK no es un tipo valido ahi,
     * lo rechaza {@link com.renaser.os.habits.domain.model.guia.AdjuntoGuia#deArchivo}. */
    public TipoMedioGuia toDomain() {
        return switch (this) {
            case LINK -> TipoMedioGuia.ENLACE;
            case IMAGE -> TipoMedioGuia.IMAGEN;
            case AUDIO -> TipoMedioGuia.AUDIO;
        };
    }
}
