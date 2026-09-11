package com.renaser.os.chat.application.ports.in.conversacion;

import java.util.UUID;

/**
 * Deja la lista de participantes del chat de un grupo igual a la composición real del grupo.
 *
 * <p>Reconcilia contra la lista completa, no aplica diferencias. Es lo que la hace idempotente
 * y a prueba de eventos fuera de orden: correrla dos veces da el mismo resultado, y correrla
 * con un aviso viejo da el estado de AHORA, no el de entonces.
 */
public interface SincronizarParticipantesCelulaUseCase {

    ResultadoSincronizacion sincronizar(UUID celulaId);

    /**
     * @param sinConversacion {@code true} si el grupo todavía no tiene chat creado. No es un
     *                        error: la conversación se crea por su propio flujo y la próxima
     *                        corrida la encontrará.
     */
    record ResultadoSincronizacion(UUID celulaId, int agregados, int quitados, boolean sinConversacion) {
    }
}
