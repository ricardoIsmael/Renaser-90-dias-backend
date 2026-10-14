package com.renaser.os.plataforma.domain.model.despliegue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Pedidos y errores 5xx de una funcionalidad en una hora, de quienes estaban dentro del despliegue o fuera (una fila de
 * {@code funcionalidades_metricas_hora}, V104; contrato fase 2 §3.3).
 *
 * @param hora truncada a la hora, en UTC
 */
public record ConteoDeDespliegue(String clave, Instant hora, boolean dentro, long pedidos, long errores) {

    public ConteoDeDespliegue {
        Objects.requireNonNull(clave, "clave es obligatoria");
        Objects.requireNonNull(hora, "hora es obligatoria");
        if (!hora.equals(horaDe(hora))) {
            throw new IllegalArgumentException("La hora de una metrica va truncada a la hora: " + hora);
        }
        if (pedidos < 0 || errores < 0 || errores > pedidos) {
            throw new IllegalArgumentException("Un error tambien es un pedido: errores entre 0 y pedidos");
        }
    }

    /** La hora (UTC) en la que cae un instante: la de la fila donde se cuenta. */
    public static Instant horaDe(Instant instante) {
        return instante.truncatedTo(ChronoUnit.HOURS);
    }
}
