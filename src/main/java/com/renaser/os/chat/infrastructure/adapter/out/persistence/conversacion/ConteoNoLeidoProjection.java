package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import java.util.UUID;

/** Proyeccion del conteo de no-leidos en lote (ver {@link SpringDataParticipanteConversacionRepository}). */
public interface ConteoNoLeidoProjection {

    UUID getConversacionId();

    Long getConteo();
}
