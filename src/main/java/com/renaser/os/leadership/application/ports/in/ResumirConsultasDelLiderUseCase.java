package com.renaser.os.leadership.application.ports.in;

/**
 * El barrido horario del resumen que recibe el mentor cuando el líder revisó a su gente (spec 005, FR-017).
 * Lo dispara el scheduler; los tests lo llaman directo.
 */
public interface ResumirConsultasDelLiderUseCase {

    /**
     * Revisa a los mentores a quienes ahora les toca su resumen (las 20:00 de su zona) y publica un
     * {@link com.renaser.os.leadership.api.RevisionDelLiderEvent} por cada uno que tenga algo que contar.
     *
     * @return cuántos avisos se publicaron en esta corrida
     */
    int resumir();
}
