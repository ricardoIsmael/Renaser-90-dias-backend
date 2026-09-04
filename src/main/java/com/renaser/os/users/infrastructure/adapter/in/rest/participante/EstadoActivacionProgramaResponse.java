package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.renaser.os.users.application.ports.in.participante.ConsultarActivacionProgramaUseCase.EstadoActivacionPrograma;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code validStartDates} vacia cuando {@code activated} es {@code true} — no hay nada mas
 * que elegir (mismo criterio que {@code EstadoActivacionPrograma} en application).
 *
 * <p>{@code startDate} (D-84) es el Dia 1 ya elegido, {@code null} mientras no eligio. Se
 * agrega porque Plan necesita distinguir dos situaciones que hoy se ven idénticas — un plan
 * vacio — y que para el aprendiz son muy distintas: "todavia no elegiste cuando empezar" y
 * "ya elegiste, arrancas el 5 de septiembre".
 */
public record EstadoActivacionProgramaResponse(boolean activated, List<String> validStartDates, String startDate) {

    public static EstadoActivacionProgramaResponse from(EstadoActivacionPrograma estado) {
        return new EstadoActivacionProgramaResponse(estado.activado(),
                estado.fechasValidas().stream().map(LocalDate::toString).toList(),
                estado.fechaInicio() == null ? null : estado.fechaInicio().toString());
    }
}
