package com.renaser.os.chat.application.ports.out.presencia;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

/**
 * Quién tiene ahora mismo abierta cada conversación (D-221). Estado efímero y compartido entre
 * instancias, como {@link PresenciaPort}: en Redis, con vencimiento, nunca en Postgres. Si el backend
 * se cae la respuesta vuelve sola a «nadie la tiene abierta», que es la que no pierde avisos.
 */
public interface ConversacionAbiertaPort {

    void marcarAbierta(UserId usuarioId, ConversacionId conversacionId, Duration vigencia);

    /** Idempotente. */
    void marcarCerrada(UserId usuarioId, ConversacionId conversacionId);

    /** Cuáles de {@code candidatos} la tienen abierta, en una sola ida a Redis. */
    Set<UserId> laTienenAbierta(ConversacionId conversacionId, Collection<UserId> candidatos);
}
