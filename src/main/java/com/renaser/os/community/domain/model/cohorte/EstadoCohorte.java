package com.renaser.os.community.domain.model.cohorte;

/**
 * Espejo del tipo Postgres `estado_cohorte` (V1__baseline_renaser.sql:50). Los nombres
 * estan en espanol porque asi vive en la base y en el dominio (CLAUDE.MD D-36) — la
 * traduccion a ingles (PLANNED/ACTIVE/COMPLETED) para la app publicada vive solo en la
 * frontera REST (`infrastructure/adapter/in/rest`), nunca aca.
 *
 * <p>Transicion valida: solo hacia adelante y de a un paso — PLANIFICADA -> ACTIVA ->
 * COMPLETADA (community/service.ts:69-72, `isValidTransition`). No hay vuelta atras: una
 * cohorte activada no puede "des-activarse".
 */
public enum EstadoCohorte {

    PLANIFICADA(0),
    ACTIVA(1),
    COMPLETADA(2);

    private final int orden;

    EstadoCohorte(int orden) {
        this.orden = orden;
    }

    public boolean puedeTransicionarA(EstadoCohorte destino) {
        return destino.orden == this.orden + 1;
    }
}
