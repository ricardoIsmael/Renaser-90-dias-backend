package com.renaser.os.rocks.application.ports.in.cierre;

import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.CerrarElDia;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.VistaDelCierre;
import com.renaser.os.shared.domain.HechoSinRed;

/**
 * Spec 048 (FR-019, fase 3): cerrar el día lo que la persona hizo SIN RED, con la hora real en que lo hizo. Mismas reglas que con
 * red, calculadas con esa hora; nunca completa una acción ni paga puntos. Un mismo {@code idCliente} se registra una sola vez.
 */
public interface CerrarElDiaSinRedUseCase {

    /**
     * @return el cierre de {@code fecha}, con la misma forma que con red ({@code puedeCerrar} según el «hoy» de la llegada)
     * @throws com.renaser.os.shared.domain.NoAplicadoSinRedException (409 con {@code codigo}) si {@code fecha} no es el día de la hora
     *         real ({@code FUERA_DE_SU_DIA}), el servidor cambió ese cierre después ({@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}) o
     *         una acción ya no existe ({@code RECURSO_YA_NO_EXISTE}, todo o nada)
     */
    VistaDelCierre cerrar(CerrarElDia pedido, HechoSinRed sinRed);
}
