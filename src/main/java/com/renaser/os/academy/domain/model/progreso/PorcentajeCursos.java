package com.renaser.os.academy.domain.model.progreso;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculo puro del "cursosPct" del Ranking General de Comunidad (50% habitos
 * + 35% rocas + 15% cursos, decision D-43): porcentaje de avance en
 * lecciones ACCESIBLES de un participante, sumado en todos sus cursos.
 * DOMINIO PURO — el caller (ver {@code PorcentajeCursosService}) trae los
 * crudos (total y completadas de lecciones ya filtradas a cursos accesibles,
 * resueltos con consultas EN LOTE) y esta clase solo aplica la regla.
 *
 * <p>Fiel a {@code sumarProgresoCursos} (RenaserBack,
 * {@code src/features/cursos/repository.ts:824-849}) y, bit a bit, al CTE
 * {@code cursos_pct} de {@code prisma/migrations/general_ranking_scores_function.sql}
 * (RenaserBack) que la reemplazo en produccion:
 * <ul>
 *   <li>Sin cursos accesibles (total = 0) → {@link #SIN_CURSOS_ACCESIBLES} (100.0). No
 *       se castiga a quien todavia no tiene nada que cursar.</li>
 *   <li>Con cursos accesibles: {@code round(completadas / total * 1000) / 10} — la
 *       MISMA formula SQL, literal, en {@link BigDecimal} con escala 1
 *       ({@link RoundingMode#HALF_UP}, equivalente al {@code round()} de Postgres
 *       para valores no negativos). Devuelve un porcentaje con 1 decimal
 *       (p.ej. {@code 33.3}, no {@code 33}) — redondear antes a entero y recien
 *       despues ponderar en el score final daria un numero distinto del que
 *       hoy ve el aprendiz en produccion.</li>
 *   <li>El total/completadas que recibe esta clase NUNCA estuvo filtrado por
 *       dia de desbloqueo de leccion/seccion — {@code sumarProgresoCursos}
 *       nunca lo hizo (solo filtra por el gate del CURSO, no de la leccion/
 *       seccion) y la funcion SQL documenta esa asimetria como intencional
 *       ("para no cambiar de comportamiento"). Replicarla es responsabilidad
 *       de quien arma los crudos, no de esta clase — se documenta aca porque
 *       es el punto mas facil de "corregir" por error.</li>
 * </ul>
 *
 * <p>Ver AC-17 en {@code docs/MODULO_ACADEMY.md}.
 */
public final class PorcentajeCursos {

    /** Escala 1 explicita — mismo criterio que {@code cursos_pct}/el score final de
     * `general_ranking_scores_function.sql` (RenaserBack): {@code 100.0}, no {@code 100}. */
    public static final BigDecimal SIN_CURSOS_ACCESIBLES = new BigDecimal("100.0");

    private static final BigDecimal MIL = BigDecimal.valueOf(1000);
    private static final BigDecimal DIEZ = BigDecimal.TEN;

    private PorcentajeCursos() {
    }

    /**
     * @param totalLeccionesAccesibles lecciones de TODOS los cursos accesibles para el participante
     * @param leccionesCompletadas cuantas de esas ya completo (subconjunto de las accesibles)
     * @return porcentaje con escala 1, espejo exacto de {@code round(completadas/total*1000)/10}
     */
    public static BigDecimal calcular(int totalLeccionesAccesibles, int leccionesCompletadas) {
        if (totalLeccionesAccesibles <= 0) {
            return SIN_CURSOS_ACCESIBLES;
        }
        // Paso 1: (completadas * 1000) / total, redondeado a entero (HALF_UP == round() de Postgres
        // para no negativos) — asi se redondea UNA sola vez, sobre la misma magnitud que el SQL.
        BigDecimal completadasPorMil = BigDecimal.valueOf(leccionesCompletadas).multiply(MIL);
        BigDecimal redondeadoEntero = completadasPorMil
                .divide(BigDecimal.valueOf(totalLeccionesAccesibles), 0, RoundingMode.HALF_UP);
        // Paso 2: /10 — un entero dividido por 10 siempre es representable exacto con 1 decimal,
        // no hace falta redondear de nuevo (UNNECESSARY documenta esa garantia, no la asume en silencio).
        return redondeadoEntero.divide(DIEZ, 1, RoundingMode.UNNECESSARY);
    }
}
