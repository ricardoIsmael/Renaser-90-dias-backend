package com.renaser.os.rag.domain.model.analisis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Los números del análisis 80/20 en un solo lugar (spec 031, «Supuestos»): cuánto es «datos suficientes», cuántos intentos, cada cuánto
 * y cuánto como máximo por día. Se configuran con {@code renaser.analisis-80-20.*}; nada de esto está repartido por el código. Los
 * valores por defecto son los propuestos en la spec («a confirmar» con el dueño).
 *
 * @param minimoDeAccionesPorSemana  acciones planificadas que hacen falta en una semana
 * @param minimoDeDiasConAcciones    días distintos con acciones que hacen falta en una semana
 * @param minimoDeAccionesPorMes     acciones planificadas que hacen falta en un mes
 * @param minimoDeRespuestasDelRadar respuestas del Código Renaser para incluir su lectura; con menos, el análisis habla solo de las acciones
 * @param maximoDeIntentos           intentos fallidos antes de dejar el periodo sin análisis
 * @param esperaEntreIntentos        tiempo mínimo entre un intento y el siguiente del mismo periodo
 * @param diasDePuestaAlDia          cuántos días después de cerrar un periodo se sigue intentando (cubre un servidor caído o la IA apagada)
 * @param generacionesPorDia         tope global de intentos en 24 horas; al alcanzarlo se difiere sin perder nada
 */
public record ReglasDelAnalisis(int minimoDeAccionesPorSemana, int minimoDeDiasConAcciones, int minimoDeAccionesPorMes,
                                int minimoDeRespuestasDelRadar, int maximoDeIntentos, Duration esperaEntreIntentos,
                                int diasDePuestaAlDia, int generacionesPorDia) {

    public static final ReglasDelAnalisis POR_DEFECTO = new ReglasDelAnalisis(5, 3, 15, 3, 3, Duration.ofHours(6), 7, 500);

    /** Lo que falta para analizar una semana: acciones planificadas. */
    public static final String FALTAN_ACCIONES = "ACCIONES";
    /** Lo que falta para analizar una semana: días distintos con acciones. */
    public static final String FALTAN_DIAS = "DIAS";

    public ReglasDelAnalisis {
        if (minimoDeAccionesPorSemana < 1 || minimoDeDiasConAcciones < 1 || minimoDeAccionesPorMes < 1 || minimoDeRespuestasDelRadar < 1
                || maximoDeIntentos < 1 || diasDePuestaAlDia < 1 || generacionesPorDia < 1) {
            throw new IllegalArgumentException("Los mínimos, los intentos, los días y el tope diario son de 1 en adelante");
        }
        if (esperaEntreIntentos == null || esperaEntreIntentos.isNegative()) {
            throw new IllegalArgumentException("La espera entre intentos no puede ser negativa");
        }
    }

    /** Qué le falta al periodo para analizarse; vacío si alcanza. Los códigos los convierte en frase la app. */
    public List<String> loQueFalta(TipoDePeriodo tipo, int accionesPlanificadas, int diasConAcciones) {
        List<String> falta = new ArrayList<>();
        if (accionesPlanificadas < (tipo == TipoDePeriodo.SEMANA ? minimoDeAccionesPorSemana : minimoDeAccionesPorMes)) {
            falta.add(FALTAN_ACCIONES);
        }
        if (tipo == TipoDePeriodo.SEMANA && diasConAcciones < minimoDeDiasConAcciones) {
            falta.add(FALTAN_DIAS);
        }
        return List.copyOf(falta);
    }

    public boolean alcanza(TipoDePeriodo tipo, int accionesPlanificadas, int diasConAcciones) {
        return loQueFalta(tipo, accionesPlanificadas, diasConAcciones).isEmpty();
    }

    /** Cuántas acciones hacen falta en el periodo (para decirlo en la app). */
    public int minimoDeAcciones(TipoDePeriodo tipo) {
        return tipo == TipoDePeriodo.SEMANA ? minimoDeAccionesPorSemana : minimoDeAccionesPorMes;
    }
}
