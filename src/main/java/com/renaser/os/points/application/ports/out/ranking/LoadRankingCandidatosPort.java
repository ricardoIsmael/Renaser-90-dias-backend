package com.renaser.os.points.application.ports.out.ranking;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.util.List;

public interface LoadRankingCandidatosPort {

    List<CandidatoRanking> aprendicesActivosConPuntaje();

    /** {@code formacion}: en la que está HOY el aprendiz; el ranking de cada formación compara solo a los suyos (spec 024). */
    record CandidatoRanking(UserId participanteId, String fullName, int puntosLiga, BigDecimal coherencia,
                            Formacion formacion) {

        /** Sin formación: la Escuela. */
        public CandidatoRanking(UserId participanteId, String fullName, int puntosLiga, BigDecimal coherencia) {
            this(participanteId, fullName, puntosLiga, coherencia, Formacion.POR_DEFECTO);
        }
    }
}
