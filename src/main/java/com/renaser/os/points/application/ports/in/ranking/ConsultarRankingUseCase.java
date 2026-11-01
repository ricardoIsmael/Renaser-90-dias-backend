package com.renaser.os.points.application.ports.in.ranking;

import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ConsultarRankingUseCase {

    /**
     * El ranking expone nombre completo + id de todos los aprendices, asi que exige un
     * actor autenticado y ACTIVO. Antes este metodo no recibia actor y el endpoint quedaba
     * accesible sin ningun header — cualquiera con acceso de red obtenia el padron completo.
     */
    default List<EntradaRanking> consultar(UserId actorId, TipoRanking tipo, LocalDate fecha) {
        return consultar(actorId, tipo, fecha, null);
    }

    /**
     * Spec 024: el ranking es el de la formación de quien pide, con la posición contada entre los aprendices de esa formación. Solo Administración,
     * Alquimista y Soporte TI eligen una; sin elegir leen «Todas», cada fila con SU formación y su posición dentro de ella.
     */
    List<EntradaRanking> consultar(UserId actorId, TipoRanking tipo, LocalDate fecha, Formacion formacion);

    /** Proyección de una fila de ranking ya con el nombre resuelto (para la respuesta HTTP). */
    record EntradaRanking(UserId participanteId, String fullName, int posicion, BigDecimal puntaje, Formacion formacion) {

        /** Sin formación: la Escuela. */
        public EntradaRanking(UserId participanteId, String fullName, int posicion, BigDecimal puntaje) {
            this(participanteId, fullName, posicion, puntaje, Formacion.POR_DEFECTO);
        }
    }
}
