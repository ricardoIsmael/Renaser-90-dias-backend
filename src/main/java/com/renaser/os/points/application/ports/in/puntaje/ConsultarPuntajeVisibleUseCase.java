package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;

/**
 * Lo que muestra {@code GET /api/v1/points/{participanteId}}: el puntaje guardado con la racha
 * DERIVADA de los dias con habito cumplido, la misma que ve la persona en Hoy (E-216).
 *
 * <p>{@link ConsultarPuntajeUseCase} sigue existiendo para quien necesita el agregado tal cual se
 * guarda; este es el que va hacia afuera.
 */
public interface ConsultarPuntajeVisibleUseCase {

    /** Mismas reglas de acceso que {@link ConsultarPuntajeUseCase#consultar}. */
    PuntajeVisible consultar(UserId actorId, UserId participanteId);

    /**
     * {@code coherencia} sigue siendo la guardada: tampoco la escribe nadie hoy (ver E-216 en
     * {@code docs/BITACORA_ERRORES.md}); la que muestra Hoy sale de {@code PorcentajeRocasFinder}.
     */
    record PuntajeVisible(UserId participanteId, BigDecimal coherencia, int puntosLiga, int rachaActual,
                          int rachaMaxima) {
    }
}
