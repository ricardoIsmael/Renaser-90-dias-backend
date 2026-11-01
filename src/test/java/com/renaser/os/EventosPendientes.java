package com.renaser.os;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Espera a que terminen los avisos entre módulos que siguen en curso (el outbox de Spring Modulith). Las pruebas que borran cuentas al empezar o al terminar la usan
 * para no pisar a un listener que todavía está escribiendo sobre ellas: el traslado de formación, por ejemplo, mueve al aprendiz de comunidad y de soporte DESPUÉS
 * de que la prueba ya recibió su respuesta, y borrar su cuenta a la mitad deja a las dos transacciones esperándose.
 */
public final class EventosPendientes {

    private static final long ESPERA_MAXIMA_MS = 30_000;

    private EventosPendientes() {
    }

    /** Espera hasta 30 s a que no quede ninguno sin completar; si alguno no termina, sigue (la prueba dirá qué falló). */
    public static void esperarQueTerminen(JdbcTemplate jdbc) {
        long limite = System.currentTimeMillis() + ESPERA_MAXIMA_MS;
        while (pendientes(jdbc) > 0 && System.currentTimeMillis() < limite) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static int pendientes(JdbcTemplate jdbc) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Integer.class);
        return n == null ? 0 : n;
    }
}
