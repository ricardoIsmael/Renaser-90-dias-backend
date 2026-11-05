package com.renaser.os.calendar.domain.model.meet;

import java.time.Duration;
import java.time.Instant;

/**
 * Cuándo volver a intentar con Google (spec 040, FR-008): espera exponencial —1, 2, 4… minutos— con tope de 15 y un poco de azar,
 * máximo 8 intentos. El azar entra por parámetro ({@code azar} en [0, 1)) porque {@code domain/} no sortea nada.
 */
public final class PoliticaDeReintentos {

    public static final int MAXIMO_DE_INTENTOS = 8;
    private static final Duration BASE = Duration.ofMinutes(1);
    private static final Duration TOPE = Duration.ofMinutes(15);
    /** Hasta +25 % de azar sobre la espera, para que muchas filas fallidas a la vez no vuelvan juntas. */
    private static final double AZAR_MAXIMO = 0.25;

    private PoliticaDeReintentos() {
    }

    /**
     * El instante del próximo intento, o {@code null} si ya no hay más.
     *
     * @param intentosHechos cuántos intentos fallidos lleva la fila, contando el que acaba de fallar (&gt;= 1)
     */
    public static Instant proximoIntento(int intentosHechos, Instant ahora, double azar) {
        if (intentosHechos >= MAXIMO_DE_INTENTOS) {
            return null;
        }
        long minutos = 1L << Math.max(0, intentosHechos - 1);
        Duration espera = BASE.multipliedBy(minutos);
        if (espera.compareTo(TOPE) > 0) {
            espera = TOPE;
        }
        long extraMs = (long) (espera.toMillis() * AZAR_MAXIMO * Math.min(Math.max(azar, 0.0), 0.999999));
        return ahora.plus(espera).plusMillis(extraMs);
    }
}
