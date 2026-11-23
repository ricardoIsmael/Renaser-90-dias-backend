package com.renaser.os.points.api;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * D-367: lo que cada aprendiz hizo ESA SEMANA (de lunes a domingo, con {@code domingo} como corte) en la Coherencia, los
 * Kilómetros y el Gimnasio, para el podio semanal del chat. Sin guardar foto, como {@link RankingGeneralFinder}.
 *
 * <p><b>Las mismas personas y reglas que las pestañas del ranking</b>: aprendices activos inscritos de {@code formacion}, sin las
 * cuentas cerradas esperando su borrado (D-243). Ordenado de mayor a menor valor, con el desempate estable por id. Quien no
 * tiene dato esa semana entra con cero; quien consume decide qué hacer con los ceros.
 *
 * <p>Kilómetros y Gimnasio son lo de la semana, <b>no el acumulado del programa</b> que muestra la pestaña: se calcula como el
 * acumulado al domingo menos el acumulado al domingo anterior.
 */
public interface RankingSemanalFinder {

    /** El % de acciones diarias cumplidas de los 7 días que terminan en {@code domingo} (1 decimal). */
    List<PuestoDeLaSemana> coherencia(LocalDate domingo, Formacion formacion);

    /** Los km de la semana que termina en {@code domingo}. */
    List<PuestoDeLaSemana> kilometros(LocalDate domingo, Formacion formacion);

    /** Los días de gimnasio de la semana que termina en {@code domingo}. */
    List<PuestoDeLaSemana> gimnasio(LocalDate domingo, Formacion formacion);

    record PuestoDeLaSemana(UserId participanteId, String nombreCompleto, BigDecimal valor) {
        public PuestoDeLaSemana {
            Objects.requireNonNull(participanteId, "participanteId es obligatorio");
            Objects.requireNonNull(valor, "valor es obligatorio");
        }
    }
}
