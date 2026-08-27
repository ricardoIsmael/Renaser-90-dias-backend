package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Contrato publico de `users` para los totales historicos de habitos que necesita la
 * pantalla de logros (gap #22 PLAN_INTEGRACION_FRONTEND.md, {@code GET /profile/logros}).
 * Vive en `users.api` (no en `habits.api`) por el mismo motivo que {@code PorcentajeRocasFinder}
 * en `points.api`: `habits` ya depende de `users` para validar al actor, asi que `users` no
 * puede depender de `habits` en la otra direccion sin crear un ciclo — DIP, el proveedor
 * (adapter de persistencia de `habits`) implementa lo que el consumidor declara.
 * Agregacion en SQL, nunca trayendo registros a memoria para contarlos.
 */
public interface HabitoLogrosFinder {

    /** Cuantos {@code registros_habito} del participante llegaron a estado COMPLETADO, historico. */
    long totalHabitosCompletados(UserId participanteId);

    /** Instante del primer habito completado alguna vez, o vacio si nunca completo ninguno. */
    Optional<Instant> primerHabitoCompletadoEn(UserId participanteId);
}
