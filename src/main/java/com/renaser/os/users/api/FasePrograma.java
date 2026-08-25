package com.renaser.os.users.api;

/**
 * Fase del programa de 90 dias en la que esta un participante. Vocabulario en INGLES
 * a proposito (D-36): son los literales que la app movil ya consume tal cual
 * ({@code current_phase: "PHASE_1_REBIRTH"}, ver `mentorService.ts` del repo movil y
 * `TraineePhase` del backend viejo) — no se inventan, se copian.
 *
 * Espejo de la columna Postgres {@code fase_programa} (español, D-21): la traduccion
 * explicita vive en el mapper de persistencia de este agregado, igual que UserRole
 * vive traducido en UserPersistenceMapper.
 */
public enum FasePrograma {

    PHASE_1_REBIRTH,
    PHASE_2_DEVELOPMENT,
    PHASE_3_ALCHEMIST_WARRIOR,
    PHASE_4_ASCENSION;

    /** Fase inicial de todo participante nuevo (default de la columna Postgres). */
    public static FasePrograma initial() {
        return PHASE_1_REBIRTH;
    }
}
