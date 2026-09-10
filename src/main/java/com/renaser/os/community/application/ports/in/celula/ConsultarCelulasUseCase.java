package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.EstadoGrupo;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConsultarCelulasUseCase {

    /** ADMIN/ALCHEMIST ven todas las celulas de la cohorte; un MENTOR solo ve la propia
     * (la que lidera), o lista vacia si no lidera ninguna en esa cohorte
     * (community/service.ts:221-249). */
    List<CelulaResumen> listarPorCohorte(UserId actorId, CohorteId cohorteId);

    CelulaDetalle obtener(UserId actorId, CelulaId celulaId);

    /**
     * {@code estado} lo resuelve el SERVICIO, no el adaptador de salida ni el cliente. El dia de
     * un grupo es el de la zona del programa, y esa la fija el backend: si la decidiera el
     * telefono, dos aprendices en husos distintos verian cerrar el mismo grupo en dias distintos
     * (plan.md §3). {@code aprendicesVigentes} sale del historial, que es donde se mide el cupo.
     */
    record CelulaResumen(Celula celula, int cantidadMiembros, PerfilBasico mentor, EstadoGrupo estado,
                          int aprendicesVigentes, Integer cupoMaximo) {
    }

    record CelulaDetalle(Celula celula, PerfilBasico mentor, List<PerfilBasico> miembros, EstadoGrupo estado,
                          int aprendicesVigentes, Integer cupoMaximo) {
    }

    record PerfilBasico(UserId id, String nombreCompleto, String avatarUrl) {
    }
}
