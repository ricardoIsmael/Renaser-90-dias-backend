package com.renaser.os.users.api;

import com.renaser.os.shared.domain.CortesDeFase;

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

    // Los cortes (1-7 Fase I, 8-34 Fase II, 35-64 Fase III, 65-90 Fase IV sobre 90 dias) viven en
    // shared.domain.CortesDeFase: la MISMA regla que usa la copia del enum de `phasecontracts` (D-21: no pueden
    // importarse entre si) y que estira las fases cuando el programa dura mas (spec 022).

    /**
     * El nombre de la fase tal como lo muestra la app ({@code FASES_EN_ORDEN} de {@code useResumenHome}): lo usa el
     * mensaje del semaforo (D-277) para decir en que fase esta la persona sin que el frontend reescriba nada.
     * Si la app cambia un nombre, hay que cambiarlo aca tambien.
     */
    public String nombreVisible() {
        return switch (this) {
            case PHASE_1_REBIRTH -> "El Espejo";
            case PHASE_2_DEVELOPMENT -> "El Ciclo Alquímico";
            case PHASE_3_ALCHEMIST_WARRIOR -> "El Maestro Interno";
            case PHASE_4_ASCENSION -> "Sistema de Alto Rendimiento";
        };
    }

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
        return paraDiaPrograma(diaPrograma, CortesDeFase.DURACION_BASE);
    }

    /**
     * La fase del día en un programa de {@code duracion} días (spec 022, Q2-A): las MISMAS cuatro fases, estiradas en proporción
     * ({@link CortesDeFase}). Con 90 días da exactamente los cortes de siempre (1, 8, 35 y 65).
     */
    public static FasePrograma paraDiaPrograma(int diaPrograma, int duracion) {
        return values()[CortesDeFase.numeroDeFase(diaPrograma, duracion) - 1];
    }

    /** 1 a 4: el número de la fase (la app lo muestra como «Fase 2»). */
    public int numero() {
        return ordinal() + 1;
    }
}
