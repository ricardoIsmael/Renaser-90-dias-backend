package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;

public enum HabitEvidenceRequirementDto {
    OPTIONAL,
    REQUIRED;

    public static HabitEvidenceRequirementDto from(ExigenciaEvidencia exigencia) {
        return exigencia == ExigenciaEvidencia.OBLIGATORIA ? REQUIRED : OPTIONAL;
    }

    public ExigenciaEvidencia toDomain() {
        return this == REQUIRED ? ExigenciaEvidencia.OBLIGATORIA : ExigenciaEvidencia.OPCIONAL;
    }
}
