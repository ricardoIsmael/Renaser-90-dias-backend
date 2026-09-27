package com.renaser.os.points.application.ports.in.semaforo;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * Los días en que una cuenta estuvo suspendida no se miden (D-209, decisión del dueño del
 * 2026-09-27). {@code points} se entera por {@code users.api.EstadoDeCuentaCambiadoEvent} y anota el
 * tramo en su propia tabla; el barrido y las lecturas lo toman del calendario de la persona.
 *
 * <p>Los dos métodos son idempotentes: el outbox de Modulith entrega cada evento al menos una vez, y
 * puede reintentar uno viejo después de uno nuevo.
 */
public interface RegistrarSuspensionDelSemaforoUseCase {

    /** La cuenta pasó a suspendida en {@code instante}: desde ese día local no se mide. */
    void alSuspender(UserId usuario, Instant instante);

    /** La cuenta dejó de estar suspendida en {@code instante}: ese día tampoco se mide; desde el siguiente, sí. */
    void alReactivar(UserId usuario, Instant instante);
}
