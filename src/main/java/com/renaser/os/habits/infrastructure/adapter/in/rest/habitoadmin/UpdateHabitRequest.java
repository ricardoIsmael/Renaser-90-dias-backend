package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import jakarta.validation.constraints.NotNull;

/**
 * Espejo de {@code UpdateHabitInput} (`habitsAdmin.ts`) — con dos decisiones documentadas
 * respecto del tipo TS del cliente, que lo declara {@code Partial<Omit<CreateHabitInput,"title">>}:
 *
 * <ol>
 *   <li><b>{@code habitType} se acepta en el JSON pero se ignora.</b> Cambiar el tipo de un
 *       habito despues de creado es un invariante protegido (ver
 *       {@code Habito.actualizarDetalles} javadoc) — {@code SelectorHabito.PorTipo} y el
 *       significado de los tracks ya generados dependen de que no cambie. No se mapea a
 *       ningun campo del comando.</li>
 *   <li><b>Reemplazo completo, no merge parcial.</b> Aunque el tipo TS marca todo opcional,
 *       este endpoint trata {@code category}/{@code evidenceRequirement} como obligatorios
 *       en el body: el panel siempre edita sobre el formulario ya hidratado con los valores
 *       actuales (mismo patron que cualquier form de edicion), asi que "mandar el estado
 *       completo del formulario" es la forma real en que el cliente ya opera. Implementar
 *       un merge campo-por-campo agregaba una capa de "presente vs. null explicito" (ver
 *       {@code ActualizarHorarioHabitoCommand} para donde SI hizo falta) sin un beneficio
 *       real aca — decision de alcance (CLAUDE.MD §0.6), no un vacio silencioso.</li>
 * </ol>
 */
public record UpdateHabitRequest(String description, HabitTypeDto habitType, @NotNull HabitCategoryDto category,
                                  @NotNull HabitEvidenceRequirementDto evidenceRequirement, boolean isOptional,
                                  boolean mandatoryOnIntoxication) {

    public DetallesHabito toDetalles() {
        return new DetallesHabito(description, category.toClave(), evidenceRequirement.toDomain(), isOptional,
                mandatoryOnIntoxication);
    }
}
