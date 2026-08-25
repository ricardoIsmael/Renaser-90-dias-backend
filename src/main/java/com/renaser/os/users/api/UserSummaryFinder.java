package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Puerto de entrada publico: la unica forma en que otro modulo consulta a `users`. */
public interface UserSummaryFinder {

    Optional<UserSummary> findById(UserId id);

    /**
     * Version EN LOTE: resuelve varios de una sola vez. Existe para que un listado
     * (ranking, feed del Muro) no dispare una consulta por fila — el N+1 clasico.
     * Los ids inexistentes simplemente no aparecen en el mapa.
     */
    Map<UserId, UserSummary> findByIds(Collection<UserId> ids);
}
