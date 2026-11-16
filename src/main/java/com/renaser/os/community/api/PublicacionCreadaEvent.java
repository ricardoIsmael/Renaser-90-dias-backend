package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado cuando alguien crea una publicacion en el Muro (tabla `publicaciones_muro`).
 * Escuchado por `notifications` (Ola 3) para avisar a la comunidad — el listener lo agrega
 * la integracion, este modulo solo publica el hecho.
 *
 * <p>`categoriaClave` viaja null cuando la publicacion no llevo categoria (CLAUDE.MD:
 * un cliente viejo que no manda `category` sigue publicando igual, wall/schema.ts:51-53).
 *
 * <p>Spec 048, fase 2: `hechoSinRedEn` es la hora real en que se escribio una publicacion hecha sin internet (nulo cuando
 * hubo senal). `occurredAt` sigue siendo la llegada: la publicacion aparece arriba (D18), y solo el habito «Post diario»
 * cuenta el dia de la hora real (D17).
 */
public record PublicacionCreadaEvent(UUID publicacionId, UserId autorId, String categoriaClave,
                                      Instant occurredAt, Instant hechoSinRedEn) implements DomainEvent {

    public PublicacionCreadaEvent(UUID publicacionId, UserId autorId, String categoriaClave, Instant occurredAt) {
        this(publicacionId, autorId, categoriaClave, occurredAt, null);
    }
}
