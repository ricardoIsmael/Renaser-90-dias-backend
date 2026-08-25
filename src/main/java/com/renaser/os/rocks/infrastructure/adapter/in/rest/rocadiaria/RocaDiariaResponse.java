package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase.RocaDiariaVista;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record RocaDiariaResponse(UUID id, LocalDate fecha, int posicion, String titulo, String descripcion,
                                  String color, int puntajeImpacto, boolean esDelegable, String eje,
                                  UUID rocaSemanalId, LocalTime horaInicio, LocalTime horaFin, boolean completada,
                                  Instant completadaEn, int puntosOtorgados, boolean bloqueada) {

    public static RocaDiariaResponse from(RocaDiaria r) {
        return from(r, false);
    }

    public static RocaDiariaResponse from(RocaDiariaVista vista) {
        return from(vista.roca(), vista.bloqueada());
    }

    private static RocaDiariaResponse from(RocaDiaria r, boolean bloqueada) {
        UUID rocaSemanalId = r.rocaSemanalId() == null ? null : r.rocaSemanalId().value();
        return new RocaDiariaResponse(r.id().value(), r.fecha(), r.posicion(), r.titulo(), r.descripcion(),
                r.color().name(), r.puntajeImpacto(), r.esDelegable(), r.eje().name(), rocaSemanalId,
                r.horaInicio(), r.horaFin(), r.completada(), r.completadaEn(), r.puntosOtorgados(), bloqueada);
    }
}
