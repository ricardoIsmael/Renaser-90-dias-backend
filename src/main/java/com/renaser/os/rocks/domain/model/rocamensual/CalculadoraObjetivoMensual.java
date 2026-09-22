package com.renaser.os.rocks.domain.model.rocamensual;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Calcula el objetivo de un mes a partir del objetivo de 90 dias y del valor real de hoy.
 *
 * <p>La forma de la cuenta y por que unifica el hito del Mapa con la cifra del mes estan en
 * {@link CurvaDelPlan}. Aca vive lo otro: los dos topes de cordura y el redondeo.
 *
 * <p>Aritmetica pura: sin reloj, sin red, sin estado. Se prueba entera con {@code new}.
 */
public final class CalculadoraObjetivoMensual {

    /**
     * Cuantas veces el ritmo que la persona misma se puso puede exigirse antes de que la cifra sea
     * mentira. <b>Tres</b>, y el numero no es un gusto: {@code necesario > 3 × planeado} es, hecha
     * la cuenta, exactamente <i>mover en UN mes mas que todo lo que se propuso mover en 90 dias</i>.
     *
     * <p>Por que ese corte y no uno en kilos o en soles: el motor no sabe si 2 000 soles al mes son
     * mucho o poco —para alguien es su facturacion entera y para otro es el redondeo— y tampoco sabe
     * que es mucho en centimetros, en clientes o en minutos. La unica vara calibrada que hay es el
     * plan que la propia persona firmo el dia 7.
     *
     * <p><b>Lo que este tope NO detecta</b>, y conviene saberlo: una meta absurda desde el dia 1.
     * Quien puso "de 5 000 a 500 000 de facturacion" tiene un ritmo planeado igual de absurdo, asi
     * que el mes 1 dara 1,0x y la cifra se mostrara. Discutir la meta al definirla es trabajo del
     * Mapa (§4.3); aca el trabajo es no mentir sobre el ritmo.
     */
    public static final int VECES_EL_RITMO_PLANEADO = 3;

    private static final MathContext PRECISION = new MathContext(16);

    private CalculadoraObjetivoMensual() {
    }

    /**
     * El objetivo de un mes, visto desde el mes que la persona esta transitando.
     *
     * <p><b>Los meses que ya cerraron no se reescriben.</b> El mes 1 de alguien que va por el 2 sigue
     * diciendo lo que prometio el dia 7 —la curva sobre la linea base, que es exactamente el hito del
     * Mapa— porque un objetivo pasado es historia y moverlo seria borrar contra que se compara. Del
     * mes en curso en adelante manda el valor REAL de hoy, que es lo que hace que lo que no se hizo
     * no desaparezca sino que se redistribuya.
     *
     * @param numeroMes  el mes que se quiere saber (1 a 3).
     * @param mesActual  el mes que la persona esta transitando (1 a 3).
     * @param lineaBase  de donde partio el dia que definio el objetivo. {@code null} = sin dato.
     * @param valorHoy   la medicion REAL de hoy. {@code null} = todavia no midio: vale la linea base,
     *                   y asi el mes 1 da exactamente el hito del Mapa.
     * @param meta       la meta del dia 90. {@code null} = sin dato.
     * @param magnitud   que clase de cosa se mide. {@code null} = todavia no lo eligio.
     * @param topePorMes tope duro de movimiento mensual donde el mundo impone uno (el peso
     *                   corporal). {@code null} = no hay limite fisico conocido y manda el relativo.
     */
    public static ObjetivoDelMes calcular(int numeroMes, int mesActual, BigDecimal lineaBase, BigDecimal valorHoy,
                                           BigDecimal meta, Magnitud magnitud, BigDecimal topePorMes) {
        int mes = acotar(numeroMes);
        if (magnitud == null) {
            return new ObjetivoDelMes.SinCifra(mes, ObjetivoDelMes.MotivoSinCifra.SIN_TIPO);
        }
        if (magnitud == Magnitud.CLINICO) {
            return new ObjetivoDelMes.SinCifra(mes, ObjetivoDelMes.MotivoSinCifra.ACOMPANAMIENTO_CLINICO);
        }
        if (lineaBase == null || meta == null) {
            return new ObjetivoDelMes.SinCifra(mes, ObjetivoDelMes.MotivoSinCifra.SIN_DATOS);
        }
        if (lineaBase.compareTo(meta) == 0) {
            return new ObjetivoDelMes.SinCifra(mes, ObjetivoDelMes.MotivoSinCifra.SIN_RECORRIDO);
        }
        return conRecorrido(mes, acotar(mesActual), lineaBase, valorHoy == null ? lineaBase : valorHoy, meta,
                magnitud, topePorMes);
    }

    private static ObjetivoDelMes conRecorrido(int mes, int mesActual, BigDecimal base, BigDecimal hoy,
                                                BigDecimal meta, Magnitud magnitud, BigDecimal topePorMes) {
        boolean yaCerro = mes < mesActual;
        BigDecimal ancla = yaCerro ? base : hoy;
        boolean sube = meta.compareTo(base) > 0;
        if (!yaCerro && (sube ? hoy.compareTo(meta) >= 0 : hoy.compareTo(meta) <= 0)) {
            return new ObjetivoDelMes.YaAlcanzado(mes, meta);
        }
        BigDecimal falta = meta.subtract(ancla).abs();
        BigDecimal alCierre = porcionAlCierreDe(mes, mesActual, yaCerro);
        BigDecimal recorrido = falta.multiply(alCierre, PRECISION);
        BigDecimal paso = falta.multiply(alCierre.subtract(porcionAlInicioDe(mes, mesActual, yaCerro)), PRECISION);

        ObjetivoDelMes.MotivoSinCifra fuera = fueraDeAlcance(paso, base, meta, topePorMes);
        if (fuera != null) {
            return new ObjetivoDelMes.SinCifra(mes, fuera);
        }
        BigDecimal valor = sube ? ancla.add(recorrido) : ancla.subtract(recorrido);
        return new ObjetivoDelMes.ConCifra(mes, magnitud, redondear(valor, magnitud), redondear(paso, magnitud),
                redondear(falta, magnitud), sube);
    }

    /** Que fraccion de {@code falta} deberia estar cubierta al CERRAR ese mes. */
    private static BigDecimal porcionAlCierreDe(int mes, int mesActual, boolean yaCerro) {
        return yaCerro ? CurvaDelPlan.acumuladoAlCierreDe(mes) : CurvaDelPlan.porcionDelTramo(mesActual, mes);
    }

    /**
     * Que fraccion deberia estar cubierta al EMPEZAR ese mes. Restarla deja {@code paso} en "cuanto
     * hay que moverse DURANTE el mes" y no "cuanto llevo acumulado", que es otra cosa y es la que
     * se muestra al lado de la cifra.
     */
    private static BigDecimal porcionAlInicioDe(int mes, int mesActual, boolean yaCerro) {
        if (yaCerro) {
            return mes == 1 ? BigDecimal.ZERO : CurvaDelPlan.acumuladoAlCierreDe(mes - 1);
        }
        return mes <= mesActual ? BigDecimal.ZERO : CurvaDelPlan.porcionDelTramo(mesActual, mes - 1);
    }

    private static int acotar(int numeroMes) {
        return Math.min(Math.max(numeroMes, 1), MesPrograma.MESES);
    }

    /**
     * Es irreal lo que haria falta este mes? Dos umbrales, y <b>solo uno manda</b> en cada objetivo.
     *
     * <p>Donde el mundo da un limite —el peso corporal— el plan de la persona no es la vara: bajar
     * 2,5 kg en el ultimo mes es perfectamente sano aunque sea mas de lo que se habia propuesto para
     * los 90 dias, y ahi el tope relativo seria un falso positivo que le apaga la cifra a alguien que
     * todavia puede llegar. Donde no lo hay, manda {@link #VECES_EL_RITMO_PLANEADO}.
     */
    private static ObjetivoDelMes.MotivoSinCifra fueraDeAlcance(BigDecimal paso, BigDecimal base, BigDecimal meta,
                                                                 BigDecimal topePorMes) {
        if (topePorMes != null && topePorMes.signum() > 0) {
            return paso.compareTo(topePorMes) > 0 ? ObjetivoDelMes.MotivoSinCifra.RITMO_NO_SALUDABLE : null;
        }
        BigDecimal ritmoPlaneado = meta.subtract(base).abs()
                .divide(BigDecimal.valueOf(MesPrograma.MESES), PRECISION);
        BigDecimal techo = ritmoPlaneado.multiply(BigDecimal.valueOf(VECES_EL_RITMO_PLANEADO));
        return paso.compareTo(techo) > 0 ? ObjetivoDelMes.MotivoSinCifra.FUERA_DE_ALCANCE : null;
    }

    /**
     * Decimales segun el tamano del numero: 79,6666… es 79,7 y 10 000,67 es 10 001. Una escala del
     * 1 al 10 va siempre entera — "estar en 6,3/10" es precision falsa, nadie actua sobre tres
     * decimas de punto, y asi coincide con el hito que el Mapa ya dibuja para Relaciones.
     *
     * <p>Mismo criterio que usa el Mapa para redondear sus hitos: dos cifras del mismo objetivo
     * mostradas con distinta precision se leen como dos datos distintos.
     */
    static BigDecimal redondear(BigDecimal valor, Magnitud magnitud) {
        if (!magnitud.admiteDecimales()) {
            return valor.setScale(0, RoundingMode.HALF_UP);
        }
        BigDecimal abs = valor.abs();
        int decimales = abs.compareTo(BigDecimal.valueOf(100)) >= 0 ? 0
                : abs.compareTo(BigDecimal.TEN) >= 0 ? 1 : 2;
        return valor.setScale(decimales, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
