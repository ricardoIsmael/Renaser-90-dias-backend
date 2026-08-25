package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.domain.model.registro.RegistroHabito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Proyeccion explicita de salida (CLAUDE.MD §5.4.1) — nunca la entidad de dominio serializada. */
public record RegistroHabitoResponse(String id, UUID habitoId, LocalDate fechaEjecucion, int diaPrograma,
                                      String tipoDia, boolean esOpcional, String estado, int puntosOtorgados,
                                      String respuestaTexto, Integer calificacionProductividad,
                                      Instant completadoEn) {

    public static RegistroHabitoResponse from(RegistroHabito r) {
        return new RegistroHabitoResponse(r.id().toString(), r.habitoId().value(), r.fechaEjecucion(),
                r.diaPrograma(), r.tipoDia().name(), r.esOpcional(), r.estado().name(), r.puntosOtorgados(),
                r.respuestaTexto(), r.calificacionProductividad(), r.completadoEn());
    }
}
