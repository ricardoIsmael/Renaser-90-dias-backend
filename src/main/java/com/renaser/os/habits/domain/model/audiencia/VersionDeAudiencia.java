package com.renaser.os.habits.domain.model.audiencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/** Una versión de la audiencia de un hábito global: quién la guardó, cuándo, y desde qué instante cuenta. */
public record VersionDeAudiencia(HabitoId habitoId, Audiencia audiencia, Instant rigeDesde, UserId cambiadoPor,
                                 Instant cambiadoEn) {

    public VersionDeAudiencia {
        if (habitoId == null || audiencia == null || rigeDesde == null || cambiadoPor == null || cambiadoEn == null) {
            throw new IllegalArgumentException("Una versión de audiencia necesita todos sus datos");
        }
    }
}
