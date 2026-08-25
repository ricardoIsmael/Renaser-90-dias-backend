package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import jakarta.validation.constraints.Size;

/** Sin campo `puntos`: el otorgamiento SIEMPRE lo calcula el servidor (CLAUDE.MD §5.3.3). */
public record CompletarRegistroRequest(@Size(max = 4000) String respuestaTexto, Integer calificacionProductividad) {
}
