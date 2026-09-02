package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Nombres de campo en ingles: contrato HTTP viejo literal (D-36) — {@code PATCH .../habit-preferences/{habitId}}.
 *
 * <p>{@code limitTime} es OPCIONAL, y esa es una correccion, no una relajacion del contrato: 19 de los
 * 22 habitos del catalogo real no tienen hora de cierre (no vencen dentro del dia), asi que exigirla
 * hacia que este endpoint devolviera 400 para el 86% de los habitos — el aprendiz no podia cambiarle
 * la hora a casi ninguno. {@code triggerTime} si sigue siendo obligatoria: sin hora de disparo el
 * habito no se puede ubicar en la jornada.
 */
public record UpdateHabitPreferenceRequest(@NotNull LocalTime triggerTime, LocalTime limitTime,
                                            boolean reminderEnabled, Integer reminderMinutesBefore) {
}
