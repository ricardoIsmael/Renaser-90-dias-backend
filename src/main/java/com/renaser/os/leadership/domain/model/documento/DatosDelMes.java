package com.renaser.os.leadership.domain.model.documento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * El reporte del mes del cuerpo de mentores (FR-032 y FR-031): el promedio de los mentores, cuántos están debajo del 60 % y el
 * ranking con el color y las consultas respondidas de cada uno. Es el mismo reporte que ve el líder en la app, en un documento.
 *
 * @param mes       {@code AAAA-MM}
 * @param promedio  el promedio de las evaluaciones calculadas, 1 decimal; null si ninguna está calculada
 * @param ranking   de la mejor evaluación a la peor
 * @param sinMuestra los mentores sin evaluación calculada ese mes: fuera del ranking y nombrados aparte
 */
public record DatosDelMes(String mes, LocalDate desde, LocalDate hasta, String zona, BigDecimal promedio,
                          int debajoDelUmbral, List<MentorDelRanking> ranking, List<String> sinMuestra) implements DatosDelDocumento {

    public DatosDelMes {
        Objects.requireNonNull(mes, "mes es obligatorio");
        Objects.requireNonNull(desde, "desde es obligatorio");
        Objects.requireNonNull(hasta, "hasta es obligatorio");
        Objects.requireNonNull(zona, "zona es obligatoria");
        ranking = ranking == null ? List.of() : List.copyOf(ranking);
        sinMuestra = sinMuestra == null ? List.of() : List.copyOf(sinMuestra);
    }

    @Override
    public TipoDeDocumento tipo() {
        return TipoDeDocumento.MES;
    }

    /** @param respondidas las consultas que respondió en el mes; null si esa fuente no respondió, igual que {@code observaciones} */
    public record MentorDelRanking(String nombre, BigDecimal evaluacion, Integer respondidas, Integer observaciones) {

        public MentorDelRanking {
            Objects.requireNonNull(evaluacion, "evaluacion es obligatoria: sin evaluación calculada no entra al ranking");
        }
    }
}
