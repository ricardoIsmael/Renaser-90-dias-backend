package com.renaser.os.chat.infrastructure.adapter.out.redis;

import java.time.Instant;

/**
 * «Todos leyeron hasta {@code readUpTo}» (D-208). Viaja por el canal de la conversación junto a los
 * mensajes y la presencia, y se distingue por {@code event}.
 *
 * <p>Con esto cada app pasa a ✓✓ sus mensajes propios escritos en ese instante o antes, sin pedir nada
 * más: la marca es la misma para todos los que miran (ver {@code ConfirmacionDeLectura}), así que no
 * hace falta un aviso por destinatario.
 *
 * <p>No dice quién leyó ni cuándo leyó cada uno: solo lo que ya muestra el ✓✓. Sin
 * {@code conversationId}, por lo mismo que {@link PresenciaFanoutPayload}: el canal ya lo nombra.
 *
 * <p><b>Un tipo nuevo en el canal, verificado contra las apps publicadas</b> (2026-09-27): el APK de
 * producción ({@code origin/master}) descarta un {@code event} que no conoce ({@code leerEventoDelChat}
 * devuelve {@code null} y el manejador sale), y además nunca completa el CONNECT (E-331).
 */
record LecturaFanoutPayload(String event, Instant readUpTo) {

    static final String EVENTO = "READ";

    static LecturaFanoutPayload hasta(Instant leidoPorTodosHasta) {
        return new LecturaFanoutPayload(EVENTO, leidoPorTodosHasta);
    }
}
