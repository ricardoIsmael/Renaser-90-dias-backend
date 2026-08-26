package com.renaser.os.rag.application.ports.out.cuota;

import com.renaser.os.shared.domain.UserId;

/**
 * Limite diario de mensajes a Renasia por aprendiz (D-48, docs/MODULO_RAG.md §3). Vive en
 * Redis, no en Postgres: la BD esta congelada y no tiene columna de contador.
 */
public interface ControlCuotaRenasiaPort {

    /**
     * Intenta consumir una unidad de cuota del dia para {@code actorId}.
     *
     * @return {@code true} si quedaba cuota y se consumio; {@code false} si ya se agoto el
     * limite diario (el llamador debe traducir esto a {@link com.renaser.os.shared.domain.RateLimitExceededException}).
     */
    boolean intentarConsumir(UserId actorId);

    /**
     * Devuelve una unidad de cuota del dia a {@code actorId} — para cuando se consumio pero
     * el intercambio termino sin producir una respuesta real (fallo de la busqueda de
     * contexto o del streaming de IA, ver auditoria adversarial de concurrencia). No es un
     * "deshacer" estricto: si el contador ya cruzo a un dia distinto (medianoche de por
     * medio) esta llamada es un no-op sobre una clave vieja, y eso es aceptable.
     */
    void liberar(UserId actorId);
}
