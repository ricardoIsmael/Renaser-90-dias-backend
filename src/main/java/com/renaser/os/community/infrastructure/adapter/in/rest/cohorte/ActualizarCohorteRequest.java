package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import java.time.LocalDate;

/** {@code endDate} siempre se aplica (incluido {@code null} para borrarla) — CM-14,
 * simplificacion documentada frente al `'endDate' in input` de community/service.ts:165:
 * no distinguimos "omitido" de "null explicito" en el PATCH. */
public record ActualizarCohorteRequest(String name, LocalDate startDate, LocalDate endDate) {
}
