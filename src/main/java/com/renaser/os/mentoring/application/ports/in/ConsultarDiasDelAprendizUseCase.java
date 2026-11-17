package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El color y el porcentaje de cada día de un aprendiz, para que el mentor pinte la semana y el mes de su ficha (spec 055,
 * FR-011, FR-012 y FR-015). Solo lo ve quien acompaña hoy el grupo del aprendiz.
 */
public interface ConsultarDiasDelAprendizUseCase {

    /** El rango más largo que se pide de una vez: un mes visible con sus días de relleno. */
    int DIAS_MAXIMOS = 62;

    /**
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code RANGO_INVALIDO} si el rango va al revés o pasa
     *                                                                 de {@value #DIAS_MAXIMOS} días
     * @throws com.renaser.os.shared.domain.NotAuthorizedException    {@code NO_ACOMPANA} si no acompaña hoy ese grupo o el aprendiz no es de él
     */
    DiasDelAprendiz diasDe(ConsultaDiasDelAprendiz consulta);

    record ConsultaDiasDelAprendiz(UserId actorId, UUID grupoId, UUID aprendizId, LocalDate desde, LocalDate hasta) {

        public ConsultaDiasDelAprendiz {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
            Objects.requireNonNull(desde, "desde es obligatorio");
            Objects.requireNonNull(hasta, "hasta es obligatorio");
        }
    }

    /**
     * @param zona   la zona del aprendiz: sus días son los suyos, no los del servidor ni los del mentor
     * @param diaUno el Día 1 de su programa; null si todavía no lo eligió (entonces no hay días)
     * @param dias   del más viejo al más nuevo, recortados a [Día 1, hoy]; sin los de fuera del programa
     */
    record DiasDelAprendiz(String zona, LocalDate diaUno, List<DiaDelSemaforo> dias) {

        public DiasDelAprendiz {
            dias = dias == null ? List.of() : List.copyOf(dias);
        }
    }
}
