package com.renaser.os.points.domain.model.ranking;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formula del ranking general de la plataforma: 50% habitos + 35% rocas + 15% cursos.
 *
 * <p>Porte LITERAL de {@code general_ranking_scores()} del backend viejo
 * (`prisma/migrations/general_ranking_scores_function.sql`), que a su vez replicaba
 * `generalRankingService.ts`. No se recalibra ningun criterio.
 *
 * <p><b>Por que vive en el dominio y no en SQL (D-43):</b> el motivo por el que el
 * equipo viejo bajo esto a un procedimiento almacenado fue de rendimiento — el calculo
 * hacia una consulta POR aprendiz y con ~30 cuentas activas agotaba las conexiones. Ese
 * problema era el N+1, y se resuelve con consultas en lote. La formula en si es regla de
 * negocio y aca se puede probar sin levantar Postgres.
 *
 * <p><b>Redondeos, en el orden exacto del original:</b> cada componente ya llega con un
 * decimal (lo garantizan los tres finders); la ponderacion se redondea a un decimal al
 * final — {@code round((0.5*h + 0.35*r + 0.15*c) * 10) / 10} en el SQL.
 *
 * <p><b>Sin dato = 100, no 0:</b> el SQL usa {@code COALESCE(hp.pct, 100)} para habitos y
 * rocas, y {@code CASE WHEN total = 0 THEN 100} para cursos. Un aprendiz que recien
 * empieza no tiene nada calificable todavia y no se lo castiga por eso.
 */
public final class PuntajeGeneral {

    private static final BigDecimal PESO_HABITOS = new BigDecimal("0.5");
    private static final BigDecimal PESO_ROCAS = new BigDecimal("0.35");
    private static final BigDecimal PESO_CURSOS = new BigDecimal("0.15");

    /** El default de "todavia no hay nada que calificar". */
    public static final BigDecimal SIN_DATO = new BigDecimal("100.0");

    private static final int DECIMALES = 1;

    private PuntajeGeneral() {
    }

    /** Cualquiera de los tres puede venir null: significa "sin dato", no cero. */
    public static BigDecimal calcular(BigDecimal porcentajeHabitos, BigDecimal porcentajeRocas,
                                       BigDecimal porcentajeCursos) {
        BigDecimal habitos = oSinDato(porcentajeHabitos);
        BigDecimal rocas = oSinDato(porcentajeRocas);
        BigDecimal cursos = oSinDato(porcentajeCursos);

        return habitos.multiply(PESO_HABITOS)
                .add(rocas.multiply(PESO_ROCAS))
                .add(cursos.multiply(PESO_CURSOS))
                .setScale(DECIMALES, RoundingMode.HALF_UP);
    }

    private static BigDecimal oSinDato(BigDecimal porcentaje) {
        return porcentaje == null ? SIN_DATO : porcentaje;
    }
}
