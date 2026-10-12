package com.renaser.os.habits.domain.model.audiencia;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * La regla de "desde mañana" (spec 001, FR-010b): algo guardado en el instante {@code rigeDesde} rige para el día
 * local {@code dia} de una persona si ese instante cae en un día local ANTERIOR, en la zona de ESA persona. Un cambio a
 * las 23:30 de Lima es del día de Lima, aunque en UTC ya sea el día siguiente (E-91, regla 02 §1).
 */
final class VigenciaPorDia {

    private VigenciaPorDia() {
    }

    static boolean rigeEl(Instant rigeDesde, LocalDate dia, ZoneId zona) {
        return rigeDesde.atZone(zona).toLocalDate().isBefore(dia);
    }
}
