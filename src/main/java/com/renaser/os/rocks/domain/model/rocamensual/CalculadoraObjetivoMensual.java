package com.renaser.os.rocks.domain.model.rocamensual;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Calcula el objetivo de un mes a partir del objetivo de 90 dias y del valor real de hoy.
 *
 * <p>La forma de la curva y por que unifica el hito del Mapa con la cifra del mes estan en
 * {@link CurvaDelPlan}. Aca vive lo otro: el punto fijo de cada mes, los dos topes de cordura y el
 * redondeo.
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
     * El objetivo de un mes.
     *
     * <p><b>La cifra del mes NO se mueve.</b> Es el punto de la curva medido desde la linea base, o
     * sea el hito del Mapa, y vale lo mismo el dia 1 que el dia 29. Lo que cambia con el valor real
     * de hoy es {@code paso} —cuanto te falta para llegar— y con el la semana, que reparte eso.
     *
     * > <b>Corregido el 2026-09-22 (el mismo dia).</b> La primera version anclaba la cifra del mes
     * > en el valor de HOY y le aplicaba la fraccion de curva que quedaba. El efecto lo destapo un
     * > test: con base 84, meta 78 y 82 kg encima, el mes 1 pedia <b>80,4</b> en vez de los 81,6 que
     * > habia prometido. <b>La meta se corria sola mientras la persona bajaba</b> — cuanto mejor le
     * > iba, mas le pedia, y nunca llegaba. El objetivo de un mes es una promesa, no una cinta
     * > caminadora; lo que se recalcula contra la realidad es el tramo de la SEMANA
     * > ({@link ObjetivoDeLaSemana}), que es donde esa honestidad sirve.
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
        boolean sube = meta.compareTo(base) > 0;
        if (sube ? hoy.compareTo(meta) >= 0 : hoy.compareTo(meta) <= 0) {
            return new ObjetivoDelMes.YaAlcanzado(mes, meta);
        }
        BigDecimal valor = puntoDeLaCurva(mes, base, meta);
        BigDecimal desde = mes == mesActual ? hoy : puntoDeLaCurva(mes - 1, base, meta);
        BigDecimal paso = valor.subtract(desde).abs();

        ObjetivoDelMes.MotivoSinCifra fuera = fueraDeAlcance(paso, base, meta, topePorMes);
        if (fuera != null) {
            return new ObjetivoDelMes.SinCifra(mes, fuera);
        }
        return new ObjetivoDelMes.ConCifra(mes, magnitud, redondear(valor, magnitud), redondear(paso, magnitud),
                redondear(meta.subtract(hoy).abs(), magnitud), sube);
    }

    /**
     * Donde cae ese mes sobre la curva del plan, <b>medido desde la linea base</b>. Es exactamente
     * el hito del Mapa, y <b>no se mueve nunca</b>.
     *
     * <p>El mes 0 es el punto de partida: sirve para que el {@code paso} de un mes futuro sea su
     * propio tramo y no todo lo acumulado desde hoy.
     */
    private static BigDecimal puntoDeLaCurva(int mes, BigDecimal base, BigDecimal meta) {
        if (mes < 1) {
            return base;
        }
        return base.add(meta.subtract(base).multiply(CurvaDelPlan.acumuladoAlCierreDe(mes), PRECISION));
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
