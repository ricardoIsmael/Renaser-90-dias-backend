package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.domain.model.habito.Habito;

/** Espejo de {@code AdminHabit} (`habitsAdmin.ts`). Proyeccion a mano — nunca la entidad completa (CLAUDE.MD §5.4.1). */
public record AdminHabitResponse(String id, String title, String description, HabitTypeDto habitType,
                                  HabitCategoryDto category, HabitEvidenceRequirementDto evidenceRequirement,
                                  boolean isOptional, boolean isActive) {

    public static AdminHabitResponse from(Habito habito) {
        return new AdminHabitResponse(habito.id().value().toString(), habito.titulo(), habito.descripcion(),
                HabitTypeDto.from(habito.tipo()), HabitCategoryDto.fromClave(habito.categoriaClave()),
                HabitEvidenceRequirementDto.from(habito.exigenciaEvidencia()), habito.esOpcional(), habito.activo());
    }
}
