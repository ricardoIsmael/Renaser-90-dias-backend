package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.users.application.ports.in.user.GetLogrosUseCase.Logros;

/** Proyeccion a mano (CLAUDE.MD §5.4.5/§8) — nombres de campo 1:1 con {@code LogrosDatos}
 * de {@code C:\renaserPlayStore\src\types\logros.ts}, sin traducir. */
public record LogrosResponse(int programDay, Integer streak, long totalHabitsCompleted,
                              String firstHabitCompletedAt, int totalRocksCompleted, String firstRockCompletedAt,
                              int bestRocksStreakDays, long radarEntriesCount, String firstRadarEntryAt) {

    public static LogrosResponse from(Logros logros) {
        return new LogrosResponse(logros.programDay(), logros.streak(), logros.totalHabitsCompleted(),
                logros.firstHabitCompletedAt() == null ? null : logros.firstHabitCompletedAt().toString(),
                logros.totalRocksCompleted(),
                logros.firstRockCompletedAt() == null ? null : logros.firstRockCompletedAt().toString(),
                logros.bestRocksStreakDays(), logros.radarEntriesCount(),
                logros.firstRadarEntryAt() == null ? null : logros.firstRadarEntryAt().toString());
    }
}
