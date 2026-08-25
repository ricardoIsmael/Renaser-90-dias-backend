package com.renaser.os.points.domain.model.ranking;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record PosicionRanking(LocalDate fecha, TipoRanking tipo, UserId participanteId, int posicion,
                               BigDecimal puntaje) {

    public PosicionRanking {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(puntaje, "puntaje es obligatorio");
        if (posicion <= 0) {
            throw new IllegalArgumentException("La posicion debe ser > 0: " + posicion);
        }
    }
}
