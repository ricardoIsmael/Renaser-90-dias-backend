package com.renaser.os.habits.domain.model.registro;

import java.math.BigDecimal;
import java.util.List;

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
 *   sin ningun dia calificable en la ventana -&gt; 100                (coherence.ts:127, "recien empezo")
 * </pre>
 *
 * <p>Los dos redondeos son deliberados y no se colapsan en uno solo — es el
 * "doble redondeo" que docs/MODULO_HABITS.md §9 documenta como verificado
 * contra el codigo, no contra el comentario de la funcion SQL vieja (que
 * coincide, pero el codigo TypeScript es la fuente de verdad).
 */
public record PorcentajeHabitos(BigDecimal valor) {

    /** "Recien empezo, no se castiga" — coherence.ts:127. */
    public static final BigDecimal SIN_DIAS_CALIFICABLES = new BigDecimal("100.0");

    public PorcentajeHabitos {
        if (valor == null) {
            throw new IllegalArgumentException("valor no puede ser null");
        }
    }

    public static PorcentajeHabitos calcular(List<ConteoDiarioHabitos> conteosDelParticipante) {
        List<Integer> puntajesDiarios = conteosDelParticipante.stream()
                .filter(c -> c.calificables() > 0)
                .map(ConteoDiarioHabitos::puntajeDelDia)
                .toList();

        if (puntajesDiarios.isEmpty()) {
            return new PorcentajeHabitos(SIN_DIAS_CALIFICABLES);
        }

        long sumaPuntajes = puntajesDiarios.stream().mapToLong(Integer::longValue).sum();
        // round(sum*10/n) == round(avg(puntaje/100)*1000) — misma cuenta que coherence.ts:129-130,
        // reordenada para no perder precision con enteros en vez de fracciones intermedias.
        long promedioPorDiez = Math.round((sumaPuntajes * 10.0) / puntajesDiarios.size());
        return new PorcentajeHabitos(BigDecimal.valueOf(promedioPorDiez, 1));
    }
}
