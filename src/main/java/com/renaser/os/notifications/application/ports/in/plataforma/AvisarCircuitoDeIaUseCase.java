package com.renaser.os.notifications.application.ports.in.plataforma;

import java.time.Instant;
import java.util.Objects;

/**
 * Avisarle a Soporte TI que el circuito de una parte de SER se abrió o se cerró (spec 002, FR-016; contrato §7): un
 * push a cada cuenta IT_SUPPORT activa, como máximo {@code renaser.notifications.circuito-ia.maximo-por-hora} (4) por
 * hora y por parte. Los de más se omiten y quedan en el log. Idempotente por parte + instante.
 */
public interface AvisarCircuitoDeIaUseCase {

    void avisar(CambioDeCircuito cambio);

    /**
     * @param parte  {@code "ia.chat"} o {@code "ia.voz"}
     * @param estado {@code "ABIERTO"} o {@code "CERRADO"}
     */
    record CambioDeCircuito(String parte, String estado, String causa, Instant occurredAt) {

        public CambioDeCircuito {
            Objects.requireNonNull(parte, "parte es obligatoria");
            Objects.requireNonNull(estado, "estado es obligatorio");
            Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
        }
    }
}
