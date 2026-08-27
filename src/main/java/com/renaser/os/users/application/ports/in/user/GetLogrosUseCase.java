package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Gap #22 (docs/PLAN_INTEGRACION_FRONTEND.md §5, espejo de {@code GET /api/v1/profile/logros}
 * del backend viejo, {@code src/features/profile/service.ts} funcion {@code getLogros}).
 * Datos crudos para las insignias de "Logros" — el MOVIL decide cuales se ven desbloqueadas,
 * no hay lista de insignias en el servidor (misma filosofia que el endpoint original).
 *
 * <p>Self-only por diseño: el comando SOLO lleva el {@code actorId} del propio actor, igual
 * criterio que {@link GetMyFullProfileUseCase} — nunca se consultan logros de otro usuario
 * por esta via.
 */
public interface GetLogrosUseCase {

    Logros getLogros(GetLogrosQuery query);

    record GetLogrosQuery(@NotNull UserId actorId) {

        public GetLogrosQuery {
            SelfValidating.validateConstructorArgs(GetLogrosQuery.class, actorId);
        }
    }

    /**
     * Nombres de campo en ingles, 1:1 con {@code LogrosDatos} de
     * {@code C:\renaserPlayStore\src\types\logros.ts} — el DTO HTTP los serializa tal cual,
     * sin traducir.
     *
     * <p>{@code streak} queda {@code null}: es la racha de dias consecutivos con TODOS los
     * habitos del dia completos y (si habia) las 3 rocas diarias tambien completas
     * ({@code computeStreak} del backend viejo) — una pregunta dia-por-dia que ningun finder
     * publico expone hoy ({@code HabitoLogrosFinder}/{@code RocaLogrosFinder} solo dan totales
     * historicos y la MEJOR racha, no la racha ACTUAL cruzando habitos+rocas). Bloqueado
     * explicitamente, no fabricado — ver docs/BITACORA_ERRORES.md.
     */
    record Logros(int programDay, Integer streak, long totalHabitsCompleted, Instant firstHabitCompletedAt,
                   int totalRocksCompleted, Instant firstRockCompletedAt, int bestRocksStreakDays,
                   long radarEntriesCount, Instant firstRadarEntryAt) {
    }
}
