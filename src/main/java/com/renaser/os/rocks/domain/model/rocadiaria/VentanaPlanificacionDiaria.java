package com.renaser.os.rocks.domain.model.rocadiaria;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Ventana de planificación nocturna (R-04): abre a las 18:00 hora local del
 * participante y corre hasta el final de ese mismo día. Portado de
 * `src/features/rocks/plazos.ts` (`ventanaDiariaAbierta`) + `service.ts:539-599`.
 */
public final class VentanaPlanificacionDiaria {

    /** Hora local (0-23) a partir de la cual se abre la ventana. */
    public static final int ABRE_HORA = 18;

    private VentanaPlanificacionDiaria() {
    }

    public static boolean abierta(Instant instante, ZoneId zona) {
        ZonedDateTime local = instante.atZone(zona);
        return local.getHour() >= ABRE_HORA;
    }
}
