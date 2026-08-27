package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Panel admin de celulas/cohortes (#25, docs/PLAN_INTEGRACION_FRONTEND.md sec. 5): un
 * listado CROSS-COHORTE de todas las celulas, con el nombre/estado de su cohorte ya
 * resuelto — a diferencia de {@link ConsultarCelulasUseCase#listarPorCohorte}, que exige
 * elegir una cohorte primero. Solo ADMIN/ALCHEMIST (mismo criterio que el resto del panel
 * admin de celulas) — un MENTOR sigue viendo su celula por {@code GET /me/cell}, no este
 * dashboard.
 */
public interface ConsultarDashboardCelulasUseCase {

    List<CelulaConCohorte> dashboard(UserId actorId);

    record CelulaConCohorte(Celula celula, int cantidadMiembros, PerfilBasico mentor, Cohorte cohorte) {
    }
}
