package com.renaser.os.points.domain.model.ajuste;

import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

public record AjustePuntos(Long id, UserId participanteId, MotivoPuntos motivo, int delta, int deltaAplicado,
                            int saldoPosterior, String nota, Instant creadoEn) {

    /** Rango de la columna Postgres `smallint` (delta/delta_aplicado). */
    private static final int SMALLINT_MIN = Short.MIN_VALUE;
    private static final int SMALLINT_MAX = Short.MAX_VALUE;

    public AjustePuntos {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(motivo, "motivo es obligatorio");
        Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        requireSmallintRange(delta, "delta");
        requireSmallintRange(deltaAplicado, "deltaAplicado");
        if (saldoPosterior < 0) {
            throw new IllegalArgumentException("saldoPosterior no puede ser negativo (piso 0)");
        }
    }

    /** Registra un nuevo asiento (id null: lo asigna Postgres al persistir). */
    public static AjustePuntos registrar(UserId participanteId, MotivoPuntos motivo, ResultadoAjuste resultado,
                                          String nota, Clock clock) {
        return new AjustePuntos(null, participanteId, motivo, resultado.deltaSolicitado(),
                resultado.deltaAplicado(), resultado.saldoPosterior(), nota, clock.now());
    }

    /** Solo para el adaptador de persistencia: reconstruye un asiento ya existente. */
    public static AjustePuntos rehydrate(Long id, UserId participanteId, MotivoPuntos motivo, int delta,
                                          int deltaAplicado, int saldoPosterior, String nota, Instant creadoEn) {
        return new AjustePuntos(id, participanteId, motivo, delta, deltaAplicado, saldoPosterior, nota, creadoEn);
    }

    private static void requireSmallintRange(int value, String field) {
        if (value < SMALLINT_MIN || value > SMALLINT_MAX) {
            throw new IllegalArgumentException(field + " fuera de rango smallint: " + value);
        }
    }
}
