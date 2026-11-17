package com.renaser.os.mentoring.domain.model.nota;

import java.time.Instant;
import java.util.UUID;

/** El texto que una corrección reemplazó, con el instante en que lo hizo (historia de las notas, constitución IV). */
public record VersionDeLaNota(UUID notaId, String textoAnterior, Instant reemplazadoEn) {
}
