package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * {@code POST /api/v1/leadership/report/documento}.
 *
 * @param month {@code AAAA-MM}: el mes del reporte
 */
public record PedirDocumentoDelMesRequest(@NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "El mes va como AAAA-MM, por ejemplo 2026-11")
                                          String month) {
}
