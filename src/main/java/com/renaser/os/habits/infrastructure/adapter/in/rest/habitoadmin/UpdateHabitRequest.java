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
 *   <li><b>La excepcion: {@code mandatoryOnIntoxication} ausente se CONSERVA (E-263).</b> El listado
 *       no la devolvia, asi que ningun formulario podia hidratarla y cada edicion la apagaba sin
 *       avisar; desde D-169 eso deja opcional el post de la comunidad en los dias de intoxicacion.
 *       Ausente o {@code null} = "no la toco"; {@code true}/{@code false} explicito = se aplica.</li>
 * </ol>
 */
public record UpdateHabitRequest(String description, HabitTypeDto habitType, @NotNull HabitCategoryDto category,
                                  @NotNull HabitEvidenceRequirementDto evidenceRequirement, boolean isOptional,
                                  Boolean mandatoryOnIntoxication) {

    /** Con la bandera ausente, el valor de acá no se usa: el servicio conserva la del hábito. */
    public DetallesHabito toDetalles() {
        return new DetallesHabito(description, category.toClave(), evidenceRequirement.toDomain(), isOptional,
                Boolean.TRUE.equals(mandatoryOnIntoxication));
    }

    /** El pedido no dijo nada de la bandera de intoxicación: se conserva (E-263). */
    public boolean conservaObligatorioEnIntoxicacion() {
        return mandatoryOnIntoxication == null;
    }
}
