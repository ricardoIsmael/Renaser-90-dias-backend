package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Contrato público de `users` para los 3 campos de Rocas de "Logros"
 * (gap #22, espejo de {@code GET /api/v1/profile/logros}, P-05). Vive en
 * `users.api` (no en `rocks.api`) por el mismo motivo que {@code points.api.RocasDelDiaFinder}:
 * `rocks` ya depende de `users` para validar al actor, así que `users` no puede depender de
 * `rocks` en la otra dirección sin crear un ciclo — DIP, `rocks.RocaLogrosService` implementa
 * lo que este módulo declara.
 *
 * <p>Nombres de método en inglés, deliberado: son 1:1 los mismos nombres de campo del
 * contrato JSON que consume la app (`C:\renaserPlayStore\src\types\logros.ts`), así el
 * servicio que compone la respuesta no necesita traducir nada.
 */
public interface RocaLogrosFinder {

    /** Cuántas Rocas Diarias, en toda la historia del participante, llegaron a completarse. */
    int totalRocksCompleted(UserId participanteId);

    /** Instante de la primera Roca Diaria completada, o vacío si ninguna todavía. */
    Optional<Instant> firstRockCompletedAt(UserId participanteId);

    /** Racha más larga histórica de días con las 3 Rocas Diarias completas. */
    int bestRocksStreakDays(UserId participanteId);
}
