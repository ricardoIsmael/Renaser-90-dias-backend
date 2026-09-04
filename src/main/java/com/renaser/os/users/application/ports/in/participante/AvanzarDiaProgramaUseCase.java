package com.renaser.os.users.application.ports.in.participante;

/**
 * D-66: el cron nocturno que faltaba (docs/MODULO_PHASECONTRACTS.md §0.2 lo señalaba
 * como bloqueante: "nada en el baseline garantiza que un cron la recalcule... ese cron
 * todavia no existe"). Sin parametros: recorre TODOS los participantes con el programa
 * activado, en paginas (ver {@code ListarParticipantesConProgramaActivoPort}), y avanza
 * a cada uno solo si {@code ParticipacionPrograma.sincronizarDiaDelPrograma} detecta un cambio
 * (idempotencia por dia calendario EN LA ZONA DE CADA PARTICIPANTE, no una fecha global
 * del servidor).
 */
public interface AvanzarDiaProgramaUseCase {

    ResultadoAvance avanzarParticipantesActivos();

    /**
     * @param evaluados cuantos participantes activados se revisaron
     * @param avanzados cuantos de ellos efectivamente sumaron un dia (los demas ya
     *                  habian sido avanzados hoy, estaban pausados —imposible en este
     *                  filtro, ya activados—, no habian llegado a su fecha de inicio, o
     *                  ya estaban en el dia 90)
     */
    record ResultadoAvance(int evaluados, int avanzados) {
    }
}
