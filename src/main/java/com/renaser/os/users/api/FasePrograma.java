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

    /** Dia de programa (1-indexado) desde el cual arranca cada fase. Espejo EXACTO de
     * los mismos cortes que {@code phasecontracts.domain.model.contrato.FasePrograma}
     * (verificados contra docs/MODULO_PHASECONTRACTS.md §1 y el `phase.ts` del repo
     * viejo): 1-7 Fase I, 8-34 Fase II, 35-64 Fase III, 65-90 Fase IV. Duplicado a
     * proposito en las dos copias del enum (D-21: `phasecontracts` no puede importar
     * este tipo de `users`, ni al reves) — si un dia de corte cambia, hay que tocar
     * los dos lugares. */
    private static final int DIA_INICIO_FASE_2 = 8;
    private static final int DIA_INICIO_FASE_3 = 35;
    private static final int DIA_INICIO_FASE_4 = 65;

    /** Fase inicial de todo participante nuevo (default de la columna Postgres). */
    public static FasePrograma initial() {
        return PHASE_1_REBIRTH;
    }

    /**
     * Deriva la fase a partir del dia de programa — NUNCA se debe leer/confiar en un
     * valor de fase guardado sin recomputarlo, el mismo principio que
     * {@code phasecontracts.domain.model.contrato.FasePrograma} documenta en su
     * cabecera (docs/MODULO_PHASECONTRACTS.md §0.2). Quien muta {@code diaPrograma}
     * en {@link com.renaser.os.users.domain.model.participante.ParticipacionPrograma}
     * debe llamar esto en el mismo lugar para no repetir el bug real que motivo este
     * metodo (dos filas en dia 17 con fases distintas, D-66).
     */
    public static FasePrograma paraDiaPrograma(int diaPrograma) {
        if (diaPrograma >= DIA_INICIO_FASE_4) {
            return PHASE_4_ASCENSION;
        }
        if (diaPrograma >= DIA_INICIO_FASE_3) {
            return PHASE_3_ALCHEMIST_WARRIOR;
        }
        if (diaPrograma >= DIA_INICIO_FASE_2) {
            return PHASE_2_DEVELOPMENT;
        }
        return PHASE_1_REBIRTH;
    }
}
