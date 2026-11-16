package com.renaser.os.habits.infrastructure.adapter.in.rest.espiritu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituSinRedUseCase.ResultadoEntregaSinRed;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.ResultadoEntrega;

/**
 * @param motivoSinPuntos spec 048 (#186, FR-015), aditivo: solo la entrega hecha sin red lo trae, y solo si el hábito «Pastilla Renacer»
 *                        quedó sin puntos ({@code HORA_FUTURA} o {@code LLEGO_DESPUES_DE_48_H}). Si es {@code null} el campo no sale: sin
 *                        cabeceras la respuesta es exactamente {@code {"onTime":…}}, como antes
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmitSpiritSummaryResponse(boolean onTime, String motivoSinPuntos) {

    public static SubmitSpiritSummaryResponse from(ResultadoEntrega resultado) {
        return new SubmitSpiritSummaryResponse(resultado.aTiempo(), null);
    }

    public static SubmitSpiritSummaryResponse from(ResultadoEntregaSinRed resultado) {
        return new SubmitSpiritSummaryResponse(resultado.aTiempo(), resultado.motivoSinPuntos());
    }
}
