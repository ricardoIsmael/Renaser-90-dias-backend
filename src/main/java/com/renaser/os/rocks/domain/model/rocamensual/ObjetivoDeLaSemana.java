package com.renaser.os.rocks.domain.model.rocamensual;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * El tramo de ESTA SEMANA, calculado desde el objetivo del mes.
 *
 * <h2>El escalon que faltaba</h2>
 *
 * El plan baja en cuatro niveles —90 dias, mes, semana y dia— y hasta hoy los dos del medio se los
 * tenia que inventar la persona. El del mes lo resolvio {@link CalculadoraObjetivoMensual}; este es
 * el siguiente. Pedido del dueno, con su propio ejemplo: <i>"en los 90 dias bajare 6 kg, entonces
 * por mes bajo 2 kg, y luego por semana bajare 0,5 kg"</i>.
 *
 * <h2>La cuenta, y por que es lineal aca y curva un nivel mas arriba</h2>
 *
 * <pre>
 *     objetivo de la semana = hoy + (objetivo del mes − hoy) / semanas que quedan del mes
 * </pre>
 *
 * Los meses siguen la curva 40/75/100 del manual ({@link CurvaDelPlan}) porque el negocio dice que
 * el primer mes rinde mas: el impulso del arranque, los habitos nuevos, lo que estaba suelto y se
 * ordena. <b>Dentro de un mes no hay tal cosa</b> — nadie sostiene que la primera semana de un mes
 * rinda mas que la tercera, y meter una curva ahi seria inventar una regla que nadie pidio. Se
 * reparte parejo, que ademas es lo que hace la cuenta del dueno: 2 kg en el mes, 0,5 por semana.
 *
 * <p>Y como el mes, <b>se recalcula contra el valor REAL de hoy</b>: si una semana no se movio
 * nada, la siguiente pide un poco mas en vez de repetir la misma cuota como si nada hubiera pasado.
 * Es la misma honestidad, un nivel mas abajo.
 *
 * <h2>Cuando no hay cifra</h2>
 *
 * Si el mes no tiene cifra, la semana tampoco — y por el mismo motivo, que ya viaja en
 * {@link ObjetivoDelMes.SinCifra}. No se inventa una razon nueva ni se muestra un numero que el
 * nivel de arriba decidio no mostrar.
 */
public record ObjetivoDeLaSemana(BigDecimal valor, BigDecimal paso, int semanasQueQuedan, boolean sube) {

    private static final MathContext PRECISION = new MathContext(16);

    /**
     * El tramo de la semana en curso, o {@code null} cuando el mes no lleva cifra.
     *
     * @param delMes      el objetivo del mes ya calculado. Solo {@code ConCifra} produce semana:
     *                    con la meta ya alcanzada no hay tramo que pedir, y sin cifra tampoco.
     * @param valorHoy    la medicion REAL de hoy. {@code null} = sin medicion; la usa quien llama
     *                    para pasar la linea base, igual que en el mes.
     * @param diaPrograma para saber cuantas semanas quedan del mes.
     * @param magnitud    decide el redondeo: una escala del 1 al 10 va entera.
     */
    public static ObjetivoDeLaSemana desde(ObjetivoDelMes delMes, BigDecimal valorHoy, int diaPrograma,
                                            Magnitud magnitud) {
        if (!(delMes instanceof ObjetivoDelMes.ConCifra mes) || valorHoy == null || magnitud == null) {
            return null;
        }
        int semanas = MesPrograma.semanasQueQuedanDelMes(diaPrograma);
        BigDecimal faltaDelMes = mes.valor().subtract(valorHoy);
        BigDecimal paso = faltaDelMes.abs().divide(BigDecimal.valueOf(semanas), PRECISION);
        BigDecimal valor = valorHoy.add(faltaDelMes.divide(BigDecimal.valueOf(semanas), PRECISION));
        return new ObjetivoDeLaSemana(CalculadoraObjetivoMensual.redondear(valor, magnitud),
                CalculadoraObjetivoMensual.redondear(paso, magnitud), semanas, mes.sube());
    }
}
