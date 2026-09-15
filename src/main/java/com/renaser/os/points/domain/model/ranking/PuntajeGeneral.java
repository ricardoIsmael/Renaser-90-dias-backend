package com.renaser.os.points.domain.model.ranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Formula del ranking general de la plataforma: 50% habitos + 35% rocas + 15% cursos.
 *
 * <p>Porte LITERAL de {@code general_ranking_scores()} del backend viejo
 * (`prisma/migrations/general_ranking_scores_function.sql`), que a su vez replicaba
 * `generalRankingService.ts`. No se recalibra ningun criterio.
 *
 * <p><b>Por que vive en el dominio y no en SQL (D-43):</b> el motivo por el que el equipo viejo
 * bajo esto a un procedimiento almacenado fue de rendimiento — el calculo hacia una consulta POR
 * aprendiz y con ~30 cuentas activas agotaba las conexiones. Ese problema era el N+1, y se
 * resuelve con consultas en lote. La formula en si es regla de negocio y aca se puede probar sin
 * levantar Postgres.
 *
 * <p><b>Redondeos, en el orden exacto del original:</b> cada componente ya llega con un decimal
 * (lo garantizan los tres finders); la ponderacion se redondea a un decimal al final —
 * {@code round((0.5*h + 0.35*r + 0.15*c) * 10) / 10} en el SQL.
 *
 * <p><b>El modulo sin dato NO puntua: sale del promedio y su peso se reparte entre los que si
 * tienen dato</b> (2026-09-15, D-131).
 *
 * <blockquote><b>Corregido el 2026-09-15.</b> Aca decia: <i>"Sin dato = 100, no 0: el SQL usa
 * {@code COALESCE(hp.pct, 100)} para habitos y rocas, y {@code CASE WHEN total = 0 THEN 100} para
 * cursos. Un aprendiz que recien empieza no tiene nada calificable todavia y no se lo castiga por
 * eso."</i> El porte era fiel al backend viejo y el razonamiento —no castigar a quien recien
 * empieza— es correcto. Lo que no cierra es la conclusion: rellenar con 100 no es dejar de
 * castigarlo, es <b>premiarlo</b>, y en una tabla ORDENADA eso lo pone por delante de quien si
 * viene cumpliendo. Un aprendiz sin un solo dato encabezaba el ranking general con 100.</blockquote>
 *
 * <p>Renormalizar es la unica salida que no inventa un valor: no lo castiga con un 0 por algo que
 * todavia no existe, ni lo premia con un 100 que no gano. <b>A quien tiene los tres modulos con
 * dato le da exactamente el mismo numero que antes</b> —los pesos suman 1 y la cuenta es la
 * misma—, asi que el orden de quien ya venia compitiendo no se mueve.
 */
public final class PuntajeGeneral {

    private static final BigDecimal PESO_HABITOS = new BigDecimal("0.5");
    private static final BigDecimal PESO_ROCAS = new BigDecimal("0.35");
    private static final BigDecimal PESO_CURSOS = new BigDecimal("0.15");

    private static final int DECIMALES = 1;

    private PuntajeGeneral() {
    }

    /**
     * @param porcentajeHabitos cualquiera de los tres puede venir {@code null}: es "sin dato", que
     *                          no es cero ni cien — simplemente no entra en la cuenta
     * @return el puntaje, o {@link Optional#empty()} si los TRES vinieron sin dato: ahi no hay
     *         nada que promediar, y quien llama decide que hacer con eso (el ranking lo manda al
     *         fondo de la tabla, no al frente)
     */
    public static Optional<BigDecimal> calcular(BigDecimal porcentajeHabitos, BigDecimal porcentajeRocas,
                                                 BigDecimal porcentajeCursos) {
        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal pesos = BigDecimal.ZERO;
        if (porcentajeHabitos != null) {
            suma = suma.add(porcentajeHabitos.multiply(PESO_HABITOS));
            pesos = pesos.add(PESO_HABITOS);
        }
        if (porcentajeRocas != null) {
            suma = suma.add(porcentajeRocas.multiply(PESO_ROCAS));
            pesos = pesos.add(PESO_ROCAS);
        }
        if (porcentajeCursos != null) {
            suma = suma.add(porcentajeCursos.multiply(PESO_CURSOS));
            pesos = pesos.add(PESO_CURSOS);
        }
        if (pesos.signum() == 0) {
            return Optional.empty();
        }
        // Con los tres presentes `pesos` vale 1 exacto: esto es la misma cuenta de siempre.
        return Optional.of(suma.divide(pesos, DECIMALES, RoundingMode.HALF_UP));
    }
}
