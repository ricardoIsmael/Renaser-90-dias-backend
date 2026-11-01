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
 *
 * <p>Spec 025 (aditivo): {@code previousDuration} y {@code newDuration} solo vienen cuando el ajuste fue un CAMBIO DE DURACION del programa (el
 * dia no se movio); en un ajuste de dia de siempre son {@code null}.
 */
public record UltimoAjusteDiaResponse(int previousDay, int newDay, int adjustmentDays, String motivo,
                                       String adjustedBy, Instant adjustedAt, Integer previousDuration, Integer newDuration) {

    static UltimoAjusteDiaResponse from(AjusteDiaPrograma ajuste) {
        if (ajuste == null) {
            return null;
        }
        return new UltimoAjusteDiaResponse(ajuste.diaAnterior(), ajuste.diaNuevo(), ajuste.diasAjusteNuevo(),
                ajuste.motivo(), ajuste.ajustadoPor() == null ? null : ajuste.ajustadoPor().toString(), ajuste.ajustadoEn(),
                ajuste.duracionAnterior(), ajuste.duracionNueva());
    }
}
