package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase.PedidoDeEvidencia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las tarjetas de camara que pidio el acompanante durante un turno (D-171), hasta que el turno las
 * junta para mandarlas a la app.
 *
 * <p><b>Por que en memoria y no en {@code propuestas_acompanante}.</b> Una propuesta se guarda porque
 * la confirma OTRA peticion ({@code POST .../confirmar}), que puede caer en otra instancia. La
 * tarjeta de la camara no se confirma en el servidor: la app sube la foto y completa con los
 * endpoints de siempre. Solo tiene que viajar del bucle de herramientas al stream del MISMO turno, y
 * los dos corren en este proceso (la herramienta la llama el adaptador del proveedor, dentro del
 * turno). Una tabla nueva para un dato que vive segundos seria una migracion sin nada que guardar.
 *
 * <p>Se lee por instante, igual que las propuestas: el turno pide "lo de este actor desde que
 * empece". Lo que queda se borra solo pasados {@link #RETENCION}: mas que un turno de chat y que
 * el tiempo entre una herramienta y su aviso en la voz en vivo.
 */
@Component
public class PedidosDeEvidenciaDelTurno {

    static final Duration RETENCION = Duration.ofMinutes(15);

    private final Map<UserId, List<PedidoDeEvidencia>> porActor = new ConcurrentHashMap<>();
    private final Clock clock;

    public PedidosDeEvidenciaDelTurno(Clock clock) {
        this.clock = clock;
    }

    public void pedir(UserId actorId, PedidoDeEvidencia pedido) {
        Instant limite = clock.now().minus(RETENCION);
        porActor.compute(actorId, (actor, pedidos) -> {
            List<PedidoDeEvidencia> vigentes = new ArrayList<>(pedidos == null ? List.of() : pedidos);
            vigentes.removeIf(viejo -> viejo.pedidoEn().isBefore(limite));
            vigentes.add(pedido);
            return vigentes;
        });
    }

    /** Del mas viejo al mas nuevo; si el mismo habito se pidio dos veces, queda una tarjeta. */
    public List<PedidoDeEvidencia> pedidasDesde(UserId actorId, Instant desde) {
        List<PedidoDeEvidencia> pedidos = porActor.getOrDefault(actorId, List.of());
        Map<UUID, PedidoDeEvidencia> unoPorRegistro = new LinkedHashMap<>();
        pedidos.stream().filter(pedido -> !pedido.pedidoEn().isBefore(desde))
                .forEach(pedido -> unoPorRegistro.putIfAbsent(pedido.registroId(), pedido));
        return List.copyOf(unoPorRegistro.values());
    }
}
