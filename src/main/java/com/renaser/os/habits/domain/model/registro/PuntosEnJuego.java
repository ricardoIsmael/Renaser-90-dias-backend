package com.renaser.os.habits.domain.model.registro;

import java.time.Duration;
import java.time.Instant;

/**
 * Cuantos puntos hay en juego en un registro que todavia no se completo, y hasta cuando.
 *
 * <p>Existe porque hasta ahora el puntaje solo se sabia DESPUES de completar: {@link
 * ResultadoOtorgamiento} se calculaba dentro de {@code RegistroService.completar} y nadie
 * podia preguntar "si lo hago ahora, cuanto gano". La pantalla de habitos, la de Training y
 * los avisos automaticos necesitan exactamente eso, y las tres tienen que decir el MISMO
 * numero — de ahi que se calcule en un solo lugar del dominio y no en cada llamador.
 *
 * <p><b>No inventa ninguna regla nueva.</b> Delega el calculo en {@link ResultadoOtorgamiento}
 * (D-97), que es la unica fuente de verdad del puntaje: si manana cambia la escala, este
 * record cambia solo.
 *
 * <p><b>Sobre "los puntos que se pierden":</b> deliberadamente NO se expone una penalizacion.
 * {@link RegistroHabito#expirar} otorga 0 y no descuenta nada — {@code MotivoPuntos.MISSED_HABIT}
 * existe en el enum pero HOY no lo usa ningun caso de uso del repo. Lo que se pierde al dejar
 * vencer un habito es exactamente {@link #siCompletaAhora()}: el puntaje que se habria ganado.
 * Poner un numero negativo aca seria inventar una regla de negocio que nadie confirmo.
 *
 * @param siCompletaAhora puntos que otorgaria completarlo en el instante consultado
 * @param maximo          el techo de la escala ({@link ResultadoOtorgamiento#PUNTOS_COMPLETOS})
 * @param plazo           instante despues del cual el registro ya no acepta entrega, o
 *                        {@code null} si el habito no tiene ninguna hora configurada (no vence)
 */
public record PuntosEnJuego(int siCompletaAhora, int maximo, Instant plazo) {

    /**
     * @param ventana la ventana resuelta del registro, o {@code null} si el habito no tiene
     *                ninguna hora configurada (ni de catalogo ni de preferencia)
     * @param ahora   el instante contra el que se mide
     */
    public static PuntosEnJuego de(VentanaEntrega ventana, Instant ahora) {
        if (ventana == null) {
            // Mismo criterio que RegistroService.completar (D-97): sin horario, la hora de la
            // accion es el ancla, siempre esta a tiempo y paga el puntaje completo.
            return new PuntosEnJuego(ResultadoOtorgamiento.PUNTOS_COMPLETOS,
                    ResultadoOtorgamiento.PUNTOS_COMPLETOS, null);
        }
        ResultadoOtorgamiento resultado = ResultadoOtorgamiento.calcular(ventana.instanteAncla(), ahora,
                ventana.extension());
        return new PuntosEnJuego(resultado.puntos(), ResultadoOtorgamiento.PUNTOS_COMPLETOS,
                ventana.plazoEvidencia());
    }

    /** Sin plazo no vence nunca. */
    public boolean vencido(Instant ahora) {
        return plazo != null && ahora.isAfter(plazo);
    }

    /** {@code null} cuando no hay plazo; nunca negativo cuando ya vencio. */
    public Duration restante(Instant ahora) {
        if (plazo == null) {
            return null;
        }
        Duration falta = Duration.between(ahora, plazo);
        return falta.isNegative() ? Duration.ZERO : falta;
    }
}
