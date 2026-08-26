package com.renaser.os.rocks.domain.model.dashboard;

/**
 * Ley II — bloqueo de planificación (Hueco #15, {@code planningBlocked} del
 * repo viejo, {@code rocks/service.ts:106-124}). Distinto de la VENTANA de
 * planificación ({@code VentanaPlanificacionDiaria}, que dice CUÁNDO se puede
 * planificar): esto dice cuándo dejó de ser opcional — a partir del día 31 de
 * programa, desde las 20:00 hora local, si todavía no hay 3 Rocas Diarias
 * planificadas para mañana.
 *
 * <p>{@code docs/MODULO_ROCKS.md} §1.8/§6 lo daba por no-construible ("depende
 * de datos que no son de `rocks`") junto con {@code coherenceScore} — una
 * relectura del código viejo mostró que esa nota mezclaba dos reglas
 * distintas: {@code coherenceScore} sí depende de `points`, pero
 * {@code planningBlocked} solo necesita el día de programa (ya expuesto por
 * {@code ConsultarProgresoParticipanteRocksPort}) y el conteo de Rocas
 * Diarias de mañana (dato propio de `rocks`). Se corrige acá.
 */
public final class BloqueoPlanificacion {

    /** {@code ROCKS_PHASE_START_DAY} del repo viejo: antes del día 31 de programa, nunca bloquea. */
    public static final int DIA_INICIO_FASE_ROCAS = 31;
    /** {@code PLANNING_LOCK_HOUR}: antes de esta hora local, planificar sigue siendo opcional. */
    public static final int HORA_BLOQUEO = 20;
    /** Cantidad de Rocas Diarias que debe tener planificadas mañana para no bloquear. */
    public static final int ROCAS_REQUERIDAS_MANANA = 3;

    private BloqueoPlanificacion() {
    }

    public static boolean bloqueada(int diaPrograma, int horaLocal, int rocasPlanificadasManana) {
        if (diaPrograma < DIA_INICIO_FASE_ROCAS || horaLocal < HORA_BLOQUEO) {
            return false;
        }
        return rocasPlanificadasManana < ROCAS_REQUERIDAS_MANANA;
    }
}
