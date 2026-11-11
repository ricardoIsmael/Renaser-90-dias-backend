package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase.RocaDiariaVista;
import com.renaser.os.rocks.domain.model.rocadiaria.AccionDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record RocaDiariaResponse(UUID id, LocalDate fecha, int posicion, String titulo, String descripcion,
                                  String color, int puntajeImpacto, boolean esDelegable, String eje,
                                  UUID rocaSemanalId, LocalTime horaInicio, LocalTime horaFin, boolean completada,
                                  Instant completadaEn, int puntosOtorgados, boolean bloqueada,
                                  List<String> acciones, String motivoSinPuntos) {

    public static RocaDiariaResponse from(RocaDiaria r) {
        return from(r, false);
    }

    /**
     * Spec 048 (FR-019): la acción marcada sin red, con el motivo por el que quedó sin puntos ({@code LLEGO_DESPUES_DE_48_H},
     * {@code HORA_FUTURA}, {@code FUERA_DE_SU_DIA}, {@code YA_ESTABA_HECHO}) o {@code null} si sumó los suyos. Campo aditivo: en todo lo
     * demás va en {@code null}.
     */
    public static RocaDiariaResponse from(RocaDiaria r, String motivoSinPuntos) {
        RocaDiariaResponse base = from(r, false);
        return new RocaDiariaResponse(base.id(), base.fecha(), base.posicion(), base.titulo(), base.descripcion(), base.color(),
                base.puntajeImpacto(), base.esDelegable(), base.eje(), base.rocaSemanalId(), base.horaInicio(), base.horaFin(),
                base.completada(), base.completadaEn(), base.puntosOtorgados(), base.bloqueada(), base.acciones(), motivoSinPuntos);
    }

    public static RocaDiariaResponse from(RocaDiariaVista vista) {
        return from(vista.roca(), vista.bloqueada());
    }

    private static RocaDiariaResponse from(RocaDiaria r, boolean bloqueada) {
        UUID rocaSemanalId = r.rocaSemanalId() == null ? null : r.rocaSemanalId().value();
        return new RocaDiariaResponse(r.id().value(), r.fecha(), r.posicion(), r.titulo(), r.descripcion(),
                r.color().name(), r.puntajeImpacto(), r.esDelegable(), r.eje().name(), rocaSemanalId,
                r.horaInicio(), r.horaFin(), r.completada(), r.completadaEn(), r.puntosOtorgados(), bloqueada,
                r.acciones().stream().map(AccionDiaria::descripcion).toList(), null);
    }
}
