package com.renaser.os.rocks.application.ports.in.cierre;

import com.renaser.os.rocks.api.CierresDelDiaFinder.DiaDelCierre;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/**
 * El cierre de un día de la propia persona (spec 017): lo que sirve a {@code GET /api/v1/rocks/day-closure}. Hoy se puede cerrar;
 * un día anterior solo se lee ({@code puedeCerrar = false}); uno que todavía no llega es 400.
 */
public interface ConsultarCierreDelDiaUseCase {

    /**
     * @param fecha el día pedido; {@code null} = hoy, en la zona de la persona
     * @throws com.renaser.os.shared.domain.NotAuthorizedException cuenta suspendida, o personal sin su programa activado
     */
    VistaDelCierre consultar(UserId actorId, LocalDate fecha);

    /**
     * @param puedeCerrar solo si el día es hoy en la zona de la persona
     * @param semana      las acciones de la semana hasta hoy; {@code null} si no es hoy, si no eligió su Día 1 o si la semana no
     *                    tiene acciones planificadas hasta hoy
     */
    record VistaDelCierre(DiaDelCierre dia, boolean puedeCerrar, AvanceDeLaSemana semana) {
    }

    record AvanceDeLaSemana(int cumplidas, int planificadas) {
    }
}
