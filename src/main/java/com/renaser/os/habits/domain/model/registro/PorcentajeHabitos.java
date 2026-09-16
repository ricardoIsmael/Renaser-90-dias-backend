package com.renaser.os.habits.domain.model.registro;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * % de cumplimiento de habitos de UN participante sobre una ventana de dias —
 * Ley VI, traduccion literal de {@code averageCompletionForDates}
 * (repo viejo, {@code src/lib/coherence.ts:114-131}; ver docs/MODULO_HABITS.md
 * §9 paso 0, D-43 en docs/MODULOS_A_AVANZAR.md §8):
 *
 * <pre>
 *   por cada dia con calificables() &gt; 0:
 *     puntajeDelDia = round(completados / calificables * 100)     REDONDEO 1 (coherence.ts:97)
 *   promedio = round(avg(puntajeDelDia) * 10) / 10                REDONDEO 2, 1 decimal (coherence.ts:130)
 *   sin ningun dia calificable en la ventana -&gt; SIN DATO           (antes 100; ver abajo)
 * </pre>
 *
 * <blockquote><b>Corregido el 2026-09-16.</b> La ventana vacia devolvia {@code 100.0}
 * ("recien empezo, no se castiga", coherence.ts:127). En una tabla ORDENADA eso no es dejar de
 * castigar, es premiar: un aprendiz en dia 0, sin un solo track, entraba al Ranking General con
 * habitos = 100 —la mitad del puntaje— y encabezaba la tabla por delante de quien viene cumpliendo
 * todos los dias. Visto en produccion el 2026-09-16. Es el mismo razonamiento de D-128 (rocas) y
 * D-131 ({@code PuntajeGeneral}): sin dato no es cero ni cien, es <b>sin dato</b>, y el modulo sale
 * del promedio. Quien si tiene dias calificables recibe exactamente el mismo numero que
 * antes.</blockquote>
 *
 * <p>Los dos redondeos son deliberados y no se colapsan en uno solo — es el
 * "doble redondeo" que docs/MODULO_HABITS.md §9 documenta como verificado
 * contra el codigo, no contra el comentario de la funcion SQL vieja (que
 * coincide, pero el codigo TypeScript es la fuente de verdad).
 */
public record PorcentajeHabitos(BigDecimal valor) {


    public PorcentajeHabitos {
        if (valor == null) {
            throw new IllegalArgumentException("valor no puede ser null");
        }
    }

    /**
     * @return el porcentaje, o {@link Optional#empty()} si en la ventana no hubo un solo dia con
     *         habitos calificables: no hay de que calcular un porcentaje
     */
    public static Optional<PorcentajeHabitos> calcular(List<ConteoDiarioHabitos> conteosDelParticipante) {
        List<Integer> puntajesDiarios = conteosDelParticipante.stream()
                .filter(c -> c.calificables() > 0)
                .map(ConteoDiarioHabitos::puntajeDelDia)
                .toList();

        if (puntajesDiarios.isEmpty()) {
            return Optional.empty();
        }

        long sumaPuntajes = puntajesDiarios.stream().mapToLong(Integer::longValue).sum();
        // round(sum*10/n) == round(avg(puntaje/100)*1000) — misma cuenta que coherence.ts:129-130,
        // reordenada para no perder precision con enteros en vez de fracciones intermedias.
        long promedioPorDiez = Math.round((sumaPuntajes * 10.0) / puntajesDiarios.size());
        return Optional.of(new PorcentajeHabitos(BigDecimal.valueOf(promedioPorDiez, 1)));
    }
}
