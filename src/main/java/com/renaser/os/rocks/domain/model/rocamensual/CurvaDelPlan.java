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
 * <p><b>Como se unifico, sin tirar ninguna de las dos reglas.</b> Manda la curva —es la del manual
 * del cliente y es la que la persona confirma el dia 7— pero se conserva lo que aportaba la otra:
 * que el tramo se <b>recalcula contra el valor REAL de hoy</b> y no contra la linea base
 * (decision del dueno registrada en su momento). Eso se logra repartiendo lo que FALTA con la
 * porcion de curva que QUEDA:
 *
 * <pre>
 *     objetivo del mes = valor de hoy + (meta − valor de hoy) × porcionRestanteDe(mes)
 * </pre>
 *
 * <p>Y la porcion restante sale de la misma curva, renormalizada por lo que ya deberia estar hecho:
 *
 * <pre>
 *     porcionRestanteDe(m) = (acumulado(m) − acumulado(m−1)) / (1 − acumulado(m−1))
 *
 *     mes 1 → (0,40 − 0,00) / (1 − 0,00) = 0,400000
 *     mes 2 → (0,75 − 0,40) / (1 − 0,40) = 0,583333
 *     mes 3 → (1,00 − 0,75) / (1 − 0,75) = 1,000000
 * </pre>
 *
 * <p>Las dos propiedades que hacen que esto cierre:
 *
 * <ol>
 *   <li><b>En el mes 1, sin mediciones todavia, da exactamente el hito.</b> El valor real es la
 *       linea base, asi que {@code base + (meta − base) × 0,40} es literalmente la formula del
 *       hito del Mapa. Los dos numeros que el dueno vio distintos pasan a ser el mismo, sin tocar
 *       el Mapa.
 *   <li><b>El mes 3 siempre pide todo lo que falta</b> (porcion 1,0), que es lo que tiene que
 *       pasar: al dia 90 se llega o no se llega. Lo que no se hizo no desaparece, se redistribuye.
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

    /**
     * Que porcion de <b>lo que todavia falta hoy</b> deberia estar cubierta al cierre del mes
     * {@code hastaMes}, para quien esta transitando el mes {@code desdeMes}.
     *
     * <p>Es la curva renormalizada por lo que ya deberia estar hecho al empezar el mes en curso. Con
     * eso, una sola cuenta responde las dos preguntas que antes tenian formulas distintas:
     *
     * <ul>
     *   <li>{@code porcionDelTramo(m, m)} es <b>el objetivo de este mes</b>;
     *   <li>{@code porcionDelTramo(mesActual, 30/60/90)} son <b>los hitos</b>, recalculados contra
     *       el valor real de hoy en vez de contra la linea base.
     * </ul>
     *
     * <p>Dos casos que conviene tener presentes: el tramo que termina en el mes 3 siempre da
     * {@code 1} —al dia 90 se llega o no se llega, no hay mes siguiente donde arrastrar nada— y un
     * {@code hastaMes} anterior al mes en curso da un numero negativo o cero, que no tiene sentido
     * pedir: los meses que ya cerraron se miran con {@link #acumuladoAlCierreDe} sobre la linea
     * base, que es la promesa original y no se reescribe.
     */
    public static BigDecimal porcionDelTramo(int desdeMes, int hastaMes) {
        int desde = acotar(desdeMes);
        int hasta = acotar(hastaMes);
        BigDecimal alInicio = desde == 1 ? BigDecimal.ZERO : acumuladoAlCierreDe(desde - 1);
        BigDecimal porRecorrer = BigDecimal.ONE.subtract(alInicio);
        return acumuladoAlCierreDe(hasta).subtract(alInicio).divide(porRecorrer, PRECISION);
    }

    /** Lo que hay que cubrir en ese mes, estando en el: {@code porcionDelTramo(mes, mes)}. */
    public static BigDecimal porcionRestanteDe(int numeroMes) {
        return porcionDelTramo(numeroMes, numeroMes);
    }

    private static int acotar(int numeroMes) {
        return Math.min(Math.max(numeroMes, 1), MesPrograma.MESES);
    }
}
