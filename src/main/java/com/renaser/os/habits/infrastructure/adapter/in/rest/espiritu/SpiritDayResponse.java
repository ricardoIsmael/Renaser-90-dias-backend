package com.renaser.os.habits.infrastructure.adapter.in.rest.espiritu;

import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase.DiaEspiritu;

import java.time.Instant;

/**
 * Proyeccion explicita (CLAUDE.MD §5.4.1). Nombres en ingles: contrato HTTP viejo literal
 * (D-36, RD-1); {@code audioUrl}/{@code audioMimeType}/{@code audioSizeBytes} son campos
 * nuevos — el contrato viejo no servia el audio, lo resolvia el cliente contra Drive.
 */
public record SpiritDayResponse(int day, String title, String state, Instant unlockedAt, Instant deadlineAt,
                                 Instant submittedAt, String summaryText, String audioUrl, String audioMimeType,
                                 Integer audioSizeBytes) {

    public static SpiritDayResponse from(DiaEspiritu d) {
        return new SpiritDayResponse(d.dia(), d.titulo(), d.estado().toLowerCase(java.util.Locale.ROOT),
                d.desbloqueadoEn(), d.fechaLimite(), d.entregadoEn(), d.resumenTexto(), d.audioUrl(), d.mimeAudio(),
                d.tamanoBytes());
    }
}
