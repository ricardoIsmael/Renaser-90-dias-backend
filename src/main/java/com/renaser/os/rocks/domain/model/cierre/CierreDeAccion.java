package com.renaser.os.rocks.domain.model.cierre;

import java.time.Instant;
import java.util.Objects;

/** La respuesta de cierre que lleva una acción pendiente y cuándo se guardó (la última vez). Solo vive en {@code RocaDiaria}. */
public record CierreDeAccion(RespuestaDeCierre respuesta, Instant cerradoEn) {

    public CierreDeAccion {
        Objects.requireNonNull(respuesta, "respuesta es obligatoria");
        Objects.requireNonNull(cerradoEn, "cerradoEn es obligatorio");
    }
}
