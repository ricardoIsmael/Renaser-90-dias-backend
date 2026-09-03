package com.renaser.os.habits.infrastructure.adapter.in.rest.habito;

import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitCategoryDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitTypeDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * Alta de un habito PROPIO del aprendiz. Deliberadamente SIN {@code ambito} ni
 * {@code participanteId} — blindaje de mass-assignment (CLAUDE.MD §5.3.3): el ambito lo fuerza
 * el dominio a PERSONAL y el participante sale del actor autenticado (
 * {@code @ActorAutenticado} en el controller), nunca del cuerpo del request. Si estos dos
 * campos viajaran por HTTP, un aprendiz podria crear un habito de SISTEMA (visible para todos)
 * o un habito PERSONAL a nombre de otro aprendiz.
 *
 * <p>{@code triggerTime}/{@code limitTime} — mismos nombres de campo que
 * {@code UpdateHabitPreferenceRequest}/{@code HabitScheduleResponse} (contrato HTTP ya
 * establecido para hora de disparo/limite). {@code triggerTime} es obligatorio: sin el, el
 * habito recien creado no genera track (docs/informes/habits-personal-con-horario.md).
 * {@code limitTime} es opcional, como la mayoria de los horarios del catalogo.
 */
public record CreatePersonalHabitRequest(@NotBlank @Size(max = 120) String title, @NotNull HabitTypeDto habitType,
                                          @NotNull HabitCategoryDto category, PlantillaHabitoPersonal template,
                                          @Size(max = 200) String goalLabel, @NotNull LocalTime triggerTime,
                                          LocalTime limitTime) {
}
