package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Clase de PK compuesta para ParticipanteConversacionJpaEntity (conversacion_id, usuario_id). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipanteConversacionId implements Serializable {

    private UUID conversacionId;
    private UUID usuarioId;
}
