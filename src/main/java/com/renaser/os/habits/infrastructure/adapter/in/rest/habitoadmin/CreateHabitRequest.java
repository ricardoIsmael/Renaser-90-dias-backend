package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Espejo de {@code CreateHabitInput} (`habitsAdmin.ts`). {@code claveSistema} NO existe
 * en este DTO a proposito: el panel admin no crea claves funcionales (§5.3.3, blindaje
 * de mass-assignment — aca no es un rol, es una identidad funcional, mismo principio). */
public record CreateHabitRequest(@NotBlank @Size(max = 120) String title, String description,
                                  @NotNull HabitTypeDto habitType, @NotNull HabitCategoryDto category,
                                  @NotNull HabitEvidenceRequirementDto evidenceRequirement, boolean isOptional,
                                  boolean mandatoryOnIntoxication) {

    public DetallesHabito toDetalles() {
        return new DetallesHabito(description, category.toClave(), evidenceRequirement.toDomain(), isOptional,
                mandatoryOnIntoxication);
    }
}
