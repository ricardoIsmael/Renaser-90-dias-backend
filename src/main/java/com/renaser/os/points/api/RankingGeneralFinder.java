package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * El ranking GENERAL (75 % hábitos de los 7 días que terminan en {@code hasta} + 25 % cursos,
 * {@code PuntajeGeneral}) calculado a un corte cualquiera, sin guardar foto (D-262: el podio semanal del grupo
 * general, que se calcula con {@code hasta = domingo} y no con la foto del lunes).
 *
 * <p><b>Las mismas personas y reglas que la pestaña General</b>: aprendices activos inscritos en el programa,
 * sin las cuentas cerradas esperando su borrado (D-243). Ordenado de mayor a menor puntaje, con el mismo
 * desempate estable que la foto diaria (el id). Quien no tiene dato en ningún módulo entra con cero, como en
 * la pestaña; quien consume decide qué hacer con los ceros.
 */
public interface RankingGeneralFinder {

    List<PuestoEnElRankingGeneral> alCorte(LocalDate hasta);

    record PuestoEnElRankingGeneral(UserId participanteId, String nombreCompleto, BigDecimal puntaje) {
        public PuestoEnElRankingGeneral {
            Objects.requireNonNull(participanteId, "participanteId es obligatorio");
            Objects.requireNonNull(puntaje, "puntaje es obligatorio");
        }
    }
}
