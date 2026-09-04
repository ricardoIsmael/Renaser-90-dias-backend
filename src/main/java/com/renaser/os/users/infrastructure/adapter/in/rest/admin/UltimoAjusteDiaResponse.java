package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;

import java.time.Instant;

/**
 * El ultimo ajuste manual del dia (D-82) — para que el panel admin muestre
 * "dia 34, ajustado por X el 03/09: viaje" en vez de un numero sin explicacion.
 * {@code null} en la respuesta si nunca le movieron el dia.
 *
 * <p>{@code adjustmentDays} es el corrimiento ACUMULADO del reloj tras este ajuste
 * (`dias_ajuste_programa`), no lo que movio este ajuste solo — es el dato que explica por
 * que la graduacion de este aprendiz cae mas tarde que la de su cohorte.
 */
public record UltimoAjusteDiaResponse(int previousDay, int newDay, int adjustmentDays, String motivo,
                                       String adjustedBy, Instant adjustedAt) {

    static UltimoAjusteDiaResponse from(AjusteDiaPrograma ajuste) {
        if (ajuste == null) {
            return null;
        }
        return new UltimoAjusteDiaResponse(ajuste.diaAnterior(), ajuste.diaNuevo(), ajuste.diasAjusteNuevo(),
                ajuste.motivo(), ajuste.ajustadoPor().toString(), ajuste.ajustadoEn());
    }
}
