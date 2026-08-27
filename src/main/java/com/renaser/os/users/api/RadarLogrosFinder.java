package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Contrato publico de `users` para los totales historicos de Codigo Renaser (RADAR) que
 * necesita la pantalla de logros (gap #22 PLAN_INTEGRACION_FRONTEND.md,
 * {@code GET /profile/logros}). Vive en `users.api` por el mismo motivo que
 * {@link HabitoLogrosFinder}. Agregacion en SQL sobre `registros_radar` (append-only),
 * nunca trayendo registros a memoria para contarlos.
 */
public interface RadarLogrosFinder {

    /** Cuantos {@code registros_radar} tiene el participante, historico. */
    long totalRegistrosRadar(UserId participanteId);

    /** Instante del primer registro de RADAR, o vacio si nunca hizo check-in. */
    Optional<Instant> primerRegistroRadarEn(UserId participanteId);
}
