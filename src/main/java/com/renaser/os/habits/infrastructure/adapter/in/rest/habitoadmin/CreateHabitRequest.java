package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/** Espejo de {@code CreateHabitInput} (`habitsAdmin.ts`). {@code claveSistema} NO existe
 * en este DTO a proposito: el panel admin no crea claves funcionales (§5.3.3, blindaje
 * de mass-assignment — aca no es un rol, es una identidad funcional, mismo principio).
 *
 * <p>E-659 (issue #44), campo ADITIVO: {@code defaultTriggerTime} es la hora del habito ("HH:mm"); ausente o nula,
 * el habito vale todo el dia, como DESPERTAR. Se llama igual que en {@code CreateScheduleRequest} y
 * {@code HabitScheduleResponse}, que es donde la app ya lo lee. */
public record CreateHabitRequest(@NotBlank @Size(max = 120) String title, String description,
                                  @NotNull HabitTypeDto habitType, @NotNull HabitCategoryDto category,
                                  @NotNull HabitEvidenceRequirementDto evidenceRequirement, boolean isOptional,
                                  boolean mandatoryOnIntoxication, List<String> targetRoles,
                                  LocalTime defaultTriggerTime) {

    /** Spec 001: {@code targetRoles} ausente o nulo = para todos, como todo habito anterior a la funcion. */
    public Audiencia toAudiencia() {
        Audiencia audiencia = TargetRolesDto.aAudiencia(targetRoles);
        return audiencia == null ? Audiencia.PARA_TODOS : audiencia;
    }

    public DetallesHabito toDetalles() {
        return new DetallesHabito(description, category.toClave(), evidenceRequirement.toDomain(), isOptional,
                mandatoryOnIntoxication);
    }
}
