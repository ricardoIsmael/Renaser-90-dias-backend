package com.renaser.os.rag.application.services.herramientas;

import java.time.Duration;
import java.time.Instant;

/**
 * "hace 3 h", "hace 2 dias": cuanto paso desde un instante (2026-09-23, herramientas de
 * notificaciones y de tickets al mentor).
 *
 * <p>Se dice como espera y no como fecha a proposito: una diferencia entre dos instantes no depende
 * de la zona horaria, asi que no hay "hoy" del servidor que se cuele (regla 02) ni hace falta
 * pedirle la zona a otro modulo para decir cuando llego un aviso. El "ahora" es siempre el del
 * {@code Clock} inyectado.
 */
final class TiempoTranscurrido {

    private static final long MINUTOS_POR_HORA = 60;
    private static final long HORAS_POR_DIA = 24;

    private TiempoTranscurrido() {
    }

    /** Redondea hacia abajo y nunca es negativo: un instante "del futuro" (relojes corridos) es "recien". */
    static String desde(Instant antes, Instant ahora) {
        long minutos = Math.max(0, Duration.between(antes, ahora).toMinutes());
        if (minutos < 1) {
            return "hace menos de 1 min";
        }
        if (minutos < MINUTOS_POR_HORA) {
            return "hace " + minutos + " min";
        }
        long horas = minutos / MINUTOS_POR_HORA;
        if (horas < HORAS_POR_DIA) {
            return "hace " + horas + " h";
        }
        long dias = horas / HORAS_POR_DIA;
        return dias == 1 ? "hace 1 dia" : "hace " + dias + " dias";
    }

    /** Corta un texto largo para el modelo, sin partirlo a mitad de palabra si se puede. */
    static String recortado(String texto, int maximo) {
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.length() <= maximo) {
            return limpio;
        }
        int corte = limpio.lastIndexOf(' ', maximo);
        return limpio.substring(0, corte > maximo / 2 ? corte : maximo) + "...";
    }
}
