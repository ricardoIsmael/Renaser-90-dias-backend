package com.renaser.os.community.application.ports.in.cohorte;

import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConsultarCohortesUseCase {

    /** ADMIN/ALCHEMIST ven todas (filtradas por {@code filtroEstado} si vino); un MENTOR ve
     * solo la cohorte de la celula que lidera, o lista vacia si no lidera ninguna
     * (community/service.ts:97-121). */
    List<CohorteResumen> listar(UserId actorId, EstadoCohorte filtroEstado);

    CohorteResumen obtener(UserId actorId, CohorteId cohorteId);

    record CohorteResumen(Cohorte cohorte, int cantidadCelulas) {
    }
}
