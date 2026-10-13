package com.renaser.os.plataforma.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * Soporte TI cambió el interruptor de una funcionalidad (spec 002, FR-008; contrato §7). Sale por el outbox en la
 * misma transacción que el cambio. {@code notifications} avisa por push a IT_SUPPORT siempre, y a ADMIN y ALCHEMIST si
 * la funcionalidad es crítica o quedó apagada para todos; es idempotente por {@code occurredAt} + {@code clave}.
 *
 * <p>{@code nombre} y {@code resumen} van al final y son aditivos al contrato: el push dice «Chat de grupo: apagada
 * para Aprendiz» sin que {@code notifications} conozca el catálogo.
 *
 * @param resumen el estado nuevo en palabras, por ejemplo «Apagada para todos» o «Prendida para todos»
 */
public record FuncionalidadCambiadaEvent(String clave, boolean critica, boolean apagadaParaTodos, UserId actor,
                                         String motivo, Instant occurredAt, String nombre, String resumen)
        implements DomainEvent {

    public FuncionalidadCambiadaEvent {
        Objects.requireNonNull(clave, "clave es obligatoria");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
    }
}
