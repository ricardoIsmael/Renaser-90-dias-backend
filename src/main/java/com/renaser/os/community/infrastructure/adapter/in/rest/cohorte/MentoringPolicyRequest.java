package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code PATCH /api/v1/admin/cohorts/{id}/mentoring-policy}.
 *
 * <p>{@code expectedVersion} no es opcional: sin él, dos administradores editando a la vez se
 * pisan y el segundo no se entera. Los rangos se validan también en el dominio; acá están para
 * devolver un 400 legible en vez de dejar que reviente más adentro.
 */
public record MentoringPolicyRequest(@Min(10) @Max(15) int capacity,
                                      @NotBlank String rotation,
                                      @NotBlank String timezone,
                                      @Min(2) @Max(15) int transferDay,
                                      @Min(1) @Max(30) int inactivityDays,
                                      @Min(1) int expectedVersion) {
}
