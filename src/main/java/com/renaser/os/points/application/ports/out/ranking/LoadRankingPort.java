package com.renaser.os.points.application.ports.out.ranking;

import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface LoadRankingPort {

    /** El corte de {@code fecha} de las formaciones del alcance (spec 024); la posición de cada fila es la de SU formación. */
    List<EntradaRankingConNombre> porTipoYFecha(TipoRanking tipo, LocalDate fecha, AlcanceDeFormacion alcance);

    record EntradaRankingConNombre(UserId participanteId, String fullName, int posicion, BigDecimal puntaje,
                                   Formacion formacion) {

        /** Sin formación: la Escuela. */
        public EntradaRankingConNombre(UserId participanteId, String fullName, int posicion, BigDecimal puntaje) {
            this(participanteId, fullName, posicion, puntaje, Formacion.POR_DEFECTO);
        }
    }
}
