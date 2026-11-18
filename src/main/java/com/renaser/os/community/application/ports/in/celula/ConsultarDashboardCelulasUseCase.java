package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Panel admin de celulas/cohortes (#25, docs/PLAN_INTEGRACION_FRONTEND.md sec. 5): un
 * listado CROSS-COHORTE de todas las celulas, con el nombre/estado de su cohorte ya
 * resuelto — a diferencia de {@link ConsultarCelulasUseCase#listarPorCohorte}, que exige
 * elegir una cohorte primero. Solo ADMIN/ALCHEMIST (mismo criterio que el resto del panel
 * admin de celulas) — un MENTOR sigue viendo su celula por {@code GET /me/cell}, no este
 * dashboard.
 *
 * <blockquote><b>Corregido 2026-11-18 (spec 056, FR-010).</b> Decia «solo ADMIN/ALCHEMIST». Desde el 18/11 tambien el lider de mentores, que ve
 * unicamente los grupos de las formaciones que tiene activas.</blockquote>
 */
public interface ConsultarDashboardCelulasUseCase {

    /** Todas las formaciones: lo que veía el panel antes de la spec 022. */
    default List<CelulaConCohorte> dashboard(UserId actorId) {
        return dashboard(actorId, null);
    }

    /** @param formacion {@code null} = «Todas»; si viene, solo los grupos de esa formación (filtro «Formación» de Administración). */
    List<CelulaConCohorte> dashboard(UserId actorId, Formacion formacion);

    /** {@code cupoMaximo} (spec 056, FR-008): el tope de aprendices del grupo; {@code null} en recepción, que no tiene tope. */
    record CelulaConCohorte(Celula celula, int cantidadMiembros, PerfilBasico mentor, Cohorte cohorte, Integer cupoMaximo) {
    }
}
