package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.Participante;

public interface AgregarParticipantePort {

    /** Idempotente: si ya es participante, no hace nada (protegido ademas por la PK
     * compuesta de {@code participantes_conversacion}). */
    void agregar(Participante participante);
}
