package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.renaser.os.users.application.ports.in.participante.ConsultarActivacionProgramaUseCase.EstadoActivacionPrograma;

import java.time.LocalDate;
import java.util.List;

/** {@code validStartDates} vacia cuando {@code activated} es {@code true} — no hay nada
 * mas que elegir (mismo criterio que {@code EstadoActivacionPrograma} en application). */
public record EstadoActivacionProgramaResponse(boolean activated, List<String> validStartDates) {

    public static EstadoActivacionProgramaResponse from(EstadoActivacionPrograma estado) {
        return new EstadoActivacionProgramaResponse(estado.activado(),
                estado.fechasValidas().stream().map(LocalDate::toString).toList());
    }
}
