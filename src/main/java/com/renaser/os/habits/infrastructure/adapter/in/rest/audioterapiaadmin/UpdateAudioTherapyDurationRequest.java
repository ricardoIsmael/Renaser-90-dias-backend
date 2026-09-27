package com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapiaadmin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code durationDays} es obligatorio: el controller lo desenvuelve a {@code int} para el comando.
 *
 * <p><b>Corregido 2026-09-27 (E-370).</b> Tenía solo {@code @Positive}, que deja pasar un {@code null}: un
 * cuerpo sin el campo respondía 500 ({@code NullPointerException} al desenvolverlo) en vez del 400 de
 * validación, y antes de que el servicio mirara el rol.
 */
public record UpdateAudioTherapyDurationRequest(@NotNull @Positive Integer durationDays) {
}
