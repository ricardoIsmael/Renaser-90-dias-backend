package com.renaser.os.users.domain.model.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que quedó de una formación que la persona ya cursó (tabla {@code participaciones_anteriores}, V127): una foto, que no se edita.
 * Se arma al trasladarla a otra formación, ANTES de reiniciar su participación.
 *
 * @param puntosLiga   los puntos que tenía al irse ({@code null} si no tenía puntaje)
 * @param motivo       obligatorio si {@link ResultadoDelTraslado#DADA_POR_TERMINADA}
 */
public record ParticipacionAnterior(UUID id, UserId usuarioId, Formacion formacion, int duracionDias, LocalDate fechaInicio,
                                    LocalDate fechaFin, int diaFinal, ResultadoDelTraslado resultado, String motivo,
                                    Integer puntosLiga, BigDecimal coherencia, Integer rachaMaxima, Formacion trasladadoA,
                                    UserId trasladadoPor, Instant creadoEn) {

    public ParticipacionAnterior {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(formacion, "formacion es obligatoria");
        Objects.requireNonNull(fechaFin, "fechaFin es obligatoria");
        Objects.requireNonNull(resultado, "resultado es obligatorio");
        Objects.requireNonNull(trasladadoA, "trasladadoA es obligatoria");
        if (resultado == ResultadoDelTraslado.DADA_POR_TERMINADA && (motivo == null || motivo.isBlank())) {
            throw new IllegalArgumentException(ReglasDeTraslado.FALTA_EL_MOTIVO);
        }
    }
}
