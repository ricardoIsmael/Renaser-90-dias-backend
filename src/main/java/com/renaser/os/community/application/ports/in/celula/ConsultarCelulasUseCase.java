package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConsultarCelulasUseCase {

    /** ADMIN/ALCHEMIST ven todas las celulas de la cohorte; un MENTOR solo ve la propia
     * (la que lidera), o lista vacia si no lidera ninguna en esa cohorte
     * (community/service.ts:221-249). */
    List<CelulaResumen> listarPorCohorte(UserId actorId, CohorteId cohorteId);

    CelulaDetalle obtener(UserId actorId, CelulaId celulaId);

    record CelulaResumen(Celula celula, int cantidadMiembros, PerfilBasico mentor) {
    }

    record CelulaDetalle(Celula celula, PerfilBasico mentor, List<PerfilBasico> miembros) {
    }

    record PerfilBasico(UserId id, String nombreCompleto, String avatarUrl) {
    }
}
