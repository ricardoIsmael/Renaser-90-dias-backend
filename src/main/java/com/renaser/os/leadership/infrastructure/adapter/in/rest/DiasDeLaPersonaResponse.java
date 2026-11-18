package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarDiasDeLaPersonaUseCase.DiasDeLaPersona;
import com.renaser.os.points.api.DiaDelSemaforo;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/v1/leadership/people/{personId}/dias?desde=&hasta=} (spec 056): el color y el porcentaje de cada día de la persona.
 * <b>La misma forma</b> que el {@code /dias} del mentor (spec 055), para que la ficha del líder y la del mentor pinten lo mismo con el mismo
 * código.
 *
 * @param dias por fecha: {@code estado} MEDIDO, SIN_DATOS, PAUSADO, CUENTA_SUSPENDIDA o PENDIENTE; el color y el porcentaje solo si se midió
 */
public record DiasDeLaPersonaResponse(String zona, LocalDate diaUno, List<DiaResponse> dias) {

    static DiasDeLaPersonaResponse from(DiasDeLaPersona lectura) {
        return new DiasDeLaPersonaResponse(lectura.zona(), lectura.diaUno(), lectura.dias().stream().map(DiaResponse::from).toList());
    }

    public record DiaResponse(LocalDate fecha, String estado, String color, Integer porcentaje) {

        static DiaResponse from(DiaDelSemaforo dia) {
            return new DiaResponse(dia.fecha(), dia.estado().name(), dia.color().name(), dia.porcentaje());
        }
    }
}
