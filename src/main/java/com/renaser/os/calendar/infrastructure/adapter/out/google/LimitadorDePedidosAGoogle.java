package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.renaser.os.shared.domain.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * El límite propio de pedidos por minuto a Google (spec 040, FR-013): más estricto que el de Google (600 por minuto) para que un error de bucle no le
 * agote la cuota a la cuenta. Una ventana deslizante de 60 segundos; pasado el tope, el pedido ni sale y queda para el siguiente turno.
 */
@Component
class LimitadorDePedidosAGoogle {

    private final Clock clock;
    private final int pedidosPorMinuto;
    private final Deque<Instant> recientes = new ArrayDeque<>();

    LimitadorDePedidosAGoogle(Clock clock, @Value("${renaser.calendar.meet.pedidos-por-minuto:30}") int pedidosPorMinuto) {
        this.clock = clock;
        this.pedidosPorMinuto = pedidosPorMinuto;
    }

    /** @return {@code true} si este pedido puede salir ahora (y lo cuenta). */
    synchronized boolean intentar() {
        Instant ahora = clock.now();
        Instant desde = ahora.minusSeconds(60);
        while (!recientes.isEmpty() && !recientes.peekFirst().isAfter(desde)) {
            recientes.removeFirst();
        }
        if (recientes.size() >= pedidosPorMinuto) {
            return false;
        }
        recientes.addLast(ahora);
        return true;
    }
}
