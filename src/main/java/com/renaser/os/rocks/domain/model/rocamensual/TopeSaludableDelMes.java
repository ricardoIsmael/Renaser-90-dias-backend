package com.renaser.os.rocks.domain.model.rocamensual;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * El tope duro de movimiento mensual, <b>solo donde el mundo impone uno</b>. Hoy hay exactamente
 * uno: el peso corporal.
 *
 * <p>El 4 % del peso de hoy por mes es cerca de 1 % por semana — el extremo alto de lo que se
 * considera seguro para bajar, y razonable para subir. Va <b>en porcentaje y no en kilos fijos</b>
 * por dos razones que se pagan solas: escala con la persona (quien pesa 120 puede mover mas kilos
 * que quien pesa 55) y sobrevive a la unidad, que en el Mapa la escribe el aprendiz y bien puede
 * ser libras. Un {@code 4} a secas seria un tope de 4 libras.
 *
 * <p>Para todo lo demas no hay tope absoluto conocido y manda el relativo —
 * {@link CalculadoraObjetivoMensual#VECES_EL_RITMO_PLANEADO} veces el ritmo del plan original.
 */
public final class TopeSaludableDelMes {

    /** Fraccion del peso corporal que se puede mover en un mes sin salirse de lo sano. */
    public static final BigDecimal TOPE_PESO_POR_MES = new BigDecimal("0.04");

    /** El unico tipo de resultado del Mapa con un limite fisiologico conocido. */
    private static final String TIPO_PESO = "peso";

    private TopeSaludableDelMes() {
    }

    /**
     * El tope de este objetivo, o {@code null} cuando no hay ninguno y decide el tope relativo.
     *
     * @param tipoResultadoSalud el {@code map_health_result_type} del Mapa. Cualquier cosa que no
     *                           sea {@code peso} —y todo lo que no sea salud— no tiene tope absoluto.
     * @param referencia         el peso de hoy; la linea base si todavia no hubo medicion.
     */
    public static BigDecimal deSalud(String tipoResultadoSalud, BigDecimal referencia) {
        if (tipoResultadoSalud == null || !TIPO_PESO.equals(tipoResultadoSalud.trim().toLowerCase())) {
            return null;
        }
        if (referencia == null || referencia.signum() == 0) {
            return null;
        }
        return referencia.abs().multiply(TOPE_PESO_POR_MES, new MathContext(16));
    }
}
