package com.renaser.os.habits.infrastructure.adapter.in.rest.espiritu;

import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase.DiaEspiritu;

import java.time.Instant;

/** Proyeccion explicita (CLAUDE.MD §5.4.1). Nombres en ingles: contrato HTTP viejo literal (D-36, RD-1). */
public record SpiritDayResponse(int day, String title, String state, Instant unlockedAt, Instant deadlineAt,
                                 Instant submittedAt, String summaryText) {

    public static SpiritDayResponse from(DiaEspiritu d) {
        return new SpiritDayResponse(d.dia(), d.titulo(), d.estado().toLowerCase(java.util.Locale.ROOT),
                d.desbloqueadoEn(), d.fechaLimite(), d.entregadoEn(), d.resumenTexto());
    }
}
