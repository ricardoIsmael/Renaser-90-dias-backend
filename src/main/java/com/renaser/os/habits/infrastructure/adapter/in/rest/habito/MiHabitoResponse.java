package com.renaser.os.habits.infrastructure.adapter.in.rest.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitCategoryDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitEvidenceRequirementDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitTypeDto;

/** Proyeccion a mano — nunca la entidad completa (CLAUDE.MD §5.4.1). */
public record MiHabitoResponse(String id, String title, String description, HabitTypeDto habitType,
                                HabitCategoryDto category, HabitEvidenceRequirementDto evidenceRequirement,
                                boolean isOptional, boolean isSystemHabit) {

    public static MiHabitoResponse from(Habito habito) {
        return new MiHabitoResponse(habito.id().value().toString(), habito.titulo(), habito.descripcion(),
                HabitTypeDto.from(habito.tipo()), HabitCategoryDto.fromClave(habito.categoriaClave()),
                HabitEvidenceRequirementDto.from(habito.exigenciaEvidencia()), habito.esOpcional(),
                habito.esDeSistema());
    }
}
