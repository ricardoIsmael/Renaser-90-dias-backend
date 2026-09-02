package com.renaser.os.habits.infrastructure.adapter.in.rest.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitCategoryDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitEvidenceRequirementDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitTypeDto;

/**
 * Proyeccion a mano — nunca la entidad completa (CLAUDE.MD §5.4.1).
 *
 * <p>{@code isDeactivatable} es de solo lectura: no existe forma de que un aprendiz lo escriba
 * (no esta en {@code CreatePersonalHabitRequest} ni en {@code UpdateHabitPreferenceRequest} — ver
 * docs/informes/habits-campo-desactivable.md). Los 4 habitos con {@code isDeactivatable=false}
 * los fija la migracion V18; hoy no hay caso de uso que pueda cambiarlo en caliente.
 */
public record MiHabitoResponse(String id, String title, String description, HabitTypeDto habitType,
                                HabitCategoryDto category, HabitEvidenceRequirementDto evidenceRequirement,
                                boolean isOptional, boolean isSystemHabit, boolean isDeactivatable) {

    public static MiHabitoResponse from(Habito habito) {
        return new MiHabitoResponse(habito.id().value().toString(), habito.titulo(), habito.descripcion(),
                HabitTypeDto.from(habito.tipo()), HabitCategoryDto.fromClave(habito.categoriaClave()),
                HabitEvidenceRequirementDto.from(habito.exigenciaEvidencia()), habito.esOpcional(),
                habito.esDeSistema(), habito.desactivable());
    }
}
