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
}
