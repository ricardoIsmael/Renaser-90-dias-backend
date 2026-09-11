package com.renaser.os.community.application.ports.in.acompanamiento;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/** Entrada a recepción y paso al grupo estable. */
public interface TrasladarAprendicesUseCase {

    /**
     * Deja a un aprendiz donde le corresponde hoy. Idempotente: llamarla dos veces el mismo
     * día no abre otro intervalo.
     */
    ResultadoTraslado ubicar(UserId aprendizId);

    /**
     * Un lote del padrón. Devuelve cuántos se movieron. Paginado y sin transacción global:
     * que un aprendiz falle no puede frenar a los demás (plan.md §4).
     */
    int procesarLote(int tamanoLote);

    record ResultadoTraslado(UserId aprendizId, String destino, UUID grupoId, String motivo) {
    }
}
