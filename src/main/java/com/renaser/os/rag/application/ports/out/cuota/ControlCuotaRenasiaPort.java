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
}
