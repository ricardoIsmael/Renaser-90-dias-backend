package com.renaser.os.rocks.domain.model.rocamensual;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * La forma del plan de 90 dias: <b>40 % al dia 30, 75 % al dia 60, 100 % al dia 90</b>.
 *
 * <p>No es una division en tercios y eso es deliberado: viene del "Manual Tecnico de
 * Implementacion · Onboarding · Dia 7" §3 V08, donde los hitos son una <i>progresion</i> y no un
 * reparto lineal. La idea de negocio es que el primer mes rinde mas —el impulso del arranque, los
 * habitos nuevos, lo que estaba suelto y se ordena— y el ultimo tramo cuesta.
 *
 * <h2>Por que esta clase existe (2026-09-22)</h2>
 *
 * Habia <b>dos numeros para la misma pregunta</b> y el dueno los vio uno al lado del otro. Para un
 * objetivo de 84 a 78 kg:
 *
 * <ul>
 *   <li>el hito del Mapa para el Dia 30 decia <b>81,6 kg</b> (esta curva: 84 − 6 × 0,40);
 *   <li>la tarjeta "Este mes" del Plan decia <b>82 kg</b> (un tercio: 84 − 6 / 3).
 * </ul>
 *
 * Las dos cuentas estaban bien cada una en su terreno y las dos respondian "que tengo que lograr
 * este mes", que es una sola pregunta y no admite dos respuestas.
 *
 * <p><b>Como se unifico, sin tirar ninguna de las dos reglas.</b> La cifra de cada mes es un punto
 * FIJO de esta curva medido desde la linea base —o sea, literalmente el hito del Mapa— y lo que se
 * recalcula contra el valor REAL de hoy es el tramo de la SEMANA, que reparte lo que falta para
 * llegar a ese punto. Asi conviven las dos reglas sin que ninguna mienta:
 *
 * <pre>
 *     objetivo del mes m = base + (meta − base) × acumuladoAlCierreDe(m)
 *
 *     mes 1 → 84 + (78 − 84) × 0,40 = 81,6   ← el hito del Dia 30, clavado
 *     mes 2 → 84 + (78 − 84) × 0,75 = 79,5
 *     mes 3 → 84 + (78 − 84) × 1,00 = 78     ← la meta, siempre
 * </pre>
 *
 * <p>Las tres propiedades que hacen que esto cierre:
 *
 * <ol>
 *   <li><b>Es identica a la formula del hito del Mapa.</b> Los dos numeros que el dueno vio
 *       distintos pasan a ser el mismo, sin tocar el Mapa.
 *   <li><b>No se mueve.</b> Vale lo mismo el dia 1 que el dia 29, se haya movido la persona o no.
 *       La primera version la anclaba en el valor de hoy y la meta se corria sola mientras bajabas
 *       — ver la correccion en {@code CalculadoraObjetivoMensual}.
 *   <li><b>El mes 3 es la meta.</b> Al dia 90 se llega o no se llega.
 * </ol>
 */
public final class CurvaDelPlan {

    /**
     * Cuanto del camino total deberia estar recorrido al cierre de cada mes. El indice es
     * {@code mes − 1}. Ver el javadoc de la clase para el origen del 40 / 75 / 100.
     */
    private static final BigDecimal[] ACUMULADO_AL_CIERRE = {
            new BigDecimal("0.40"),
            new BigDecimal("0.75"),
            BigDecimal.ONE,
    };

    /** Suficiente para que 0,35/0,60 no arrastre error visible al redondear a uno o dos decimales. */
    private static final MathContext PRECISION = new MathContext(16);

    private CurvaDelPlan() {
    }

    /** Fraccion del camino total que deberia estar recorrida al cierre de ese mes: 0,40 / 0,75 / 1. */
    public static BigDecimal acumuladoAlCierreDe(int numeroMes) {
        return ACUMULADO_AL_CIERRE[acotar(numeroMes) - 1];
    }

    private static int acotar(int numeroMes) {
        return Math.min(Math.max(numeroMes, 1), MesPrograma.MESES);
    }
}
