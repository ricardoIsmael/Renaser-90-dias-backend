package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Hueco #10 — mismos campos que {@link RegistroHabitoResponse} (no se rompe el contrato
 * existente) MAS el catalogo resuelto: titulo, tipo, guia y horario.
 */
public record RegistroHabitoConCatalogoResponse(String id, UUID habitoId, LocalDate fechaEjecucion, int diaPrograma,
                                                  String tipoDia, boolean esOpcional, String estado,
                                                  int puntosOtorgados, String respuestaTexto,
                                                  Integer calificacionProductividad, Instant completadoEn,
                                                  String tituloHabito, String tipoHabito, GuiaResumenResponse guia,
                                                  LocalTime horaDisparo, LocalTime horaLimite) {

    public static RegistroHabitoConCatalogoResponse from(TrackDelDiaConCatalogo vista) {
        var r = vista.registro();
        return new RegistroHabitoConCatalogoResponse(r.id().toString(), r.habitoId().value(), r.fechaEjecucion(),
                r.diaPrograma(), r.tipoDia().name(), r.esOpcional(), r.estado().name(), r.puntosOtorgados(),
                r.respuestaTexto(), r.calificacionProductividad(), r.completadoEn(), vista.tituloHabito(),
                vista.tipoHabito() != null ? vista.tipoHabito().name() : null,
                vista.guia() != null ? GuiaResumenResponse.from(vista.guia()) : null, vista.horaDisparo(),
                vista.horaLimite());
    }
}
