package com.renaser.os.habits.infrastructure.adapter.in.rest.desbloqueo;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code active: false} pausa el habito para este aprendiz; {@code true} lo reactiva (D-87).
 *
 * <p>{@code pausedUntil} (V31, opcional) es el ULTIMO dia en que sigue pausado, en la zona del
 * aprendiz y en formato {@code yyyy-MM-dd} — "pausalo hasta el domingo". Omitirlo mantiene el
 * comportamiento de V23: pausa indefinida. Se ignora cuando {@code active} es {@code true}.
 *
 * <p>NO se valida {@code @FutureOrPresent} aca: "hasta hoy" es una pausa legitima de un solo dia,
 * y una fecha ya vencida simplemente deja el habito activo — el dominio la evalua contra el
 * calendario del aprendiz ({@code DesbloqueoHabito.estaPausadoEl}), no contra el reloj del
 * servidor, asi que rechazarla aca con la fecha del servidor reintroduciria el problema de zonas
 * de E-91.
 */
public record CambiarEstadoHabitoRequest(@NotNull Boolean active, LocalDate pausedUntil) {
}
