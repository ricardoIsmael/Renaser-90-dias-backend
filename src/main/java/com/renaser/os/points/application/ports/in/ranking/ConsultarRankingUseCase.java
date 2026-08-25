package com.renaser.os.points.application.ports.in.ranking;

import com.renaser.os.points.domain.model.ranking.TipoRanking;
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
    List<EntradaRanking> consultar(UserId actorId, TipoRanking tipo, LocalDate fecha);

    /** Proyección de una fila de ranking ya con el nombre resuelto (para la respuesta HTTP). */
    record EntradaRanking(UserId participanteId, String fullName, int posicion, BigDecimal puntaje) {
    }
}
