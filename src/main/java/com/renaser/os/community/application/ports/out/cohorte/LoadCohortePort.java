package com.renaser.os.community.application.ports.out.cohorte;

import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;

import java.util.List;
import java.util.Optional;

public interface LoadCohortePort {

    Optional<Cohorte> porId(CohorteId id);

    /** {@code filtroEstado} null = todas. Orden: mas nueva primero (community/repository.ts:24). */
    List<Cohorte> listar(EstadoCohorte filtroEstado);

    int contarCelulas(CohorteId id);
}
