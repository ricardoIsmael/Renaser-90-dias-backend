package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Escritura de {@code participantes_programa.celula_id} para quien SÍ puede validar que la
 * célula exista (gap #25, docs/PLAN_INTEGRACION_FRONTEND.md §5) — hoy solo {@code community},
 * dueño del agregado {@code Celula}. {@code users} es dueño de la columna, no de la validación
 * de existencia: quien llama debe confirmar la célula antes de invocar {@link #asignarCelula}.
 */
public interface AsignacionCelulaPort {

    void asignarCelula(UserId actorId, UserId traineeId, UUID celulaId);

    void quitarCelula(UserId actorId, UserId traineeId);
}
