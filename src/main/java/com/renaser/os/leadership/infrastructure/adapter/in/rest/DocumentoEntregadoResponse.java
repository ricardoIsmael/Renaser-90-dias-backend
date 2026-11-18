package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.GenerarDocumentoSemanalUseCase.DocumentoEntregado;

import java.time.Instant;
import java.time.LocalDate;

/**
 * El documento que se entrega (spec 056, FR-039): el enlace firmado, cuándo vence, si se reutilizó el que ya estaba y el período que cubre.
 *
 * @param venceEn     el instante en que el enlace deja de abrir; pedirlo de nuevo da otro enlace
 * @param reutilizado true si el documento ya estaba generado con esos mismos datos
 */
public record DocumentoEntregadoResponse(String url, Instant venceEn, boolean reutilizado, Semana semana) {

    static DocumentoEntregadoResponse from(DocumentoEntregado entregado) {
        return new DocumentoEntregadoResponse(entregado.url().toString(), entregado.venceEn(), entregado.reutilizado(),
                new Semana(entregado.desde(), entregado.hasta()));
    }

    /** El período del documento: el sábado y el viernes de la semana, o el primer y el último día del mes. */
    public record Semana(LocalDate desde, LocalDate hasta) {
    }
}
