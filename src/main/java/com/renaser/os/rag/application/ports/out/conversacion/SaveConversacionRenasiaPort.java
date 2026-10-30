package com.renaser.os.rag.application.ports.out.conversacion;

import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;

public interface SaveConversacionRenasiaPort {

    /**
     * Guarda la conversacion tal cual (un UPDATE si ya existe). Para CREARLA al vuelo, cuando varios
     * hilos pueden llegar a la vez por la misma persona, usar {@link #crearSiFalta}: con un
     * "?existe? -> save" las dos ven que no y la segunda revienta con {@code conversaciones_renasia_pkey}.
     */
    ConversacionRenasia save(ConversacionRenasia conversacion);

    /**
     * Crea la conversacion si todavia no existe fila para esa persona y no hace nada si ya existe
     * (E-655). La clave primaria es el usuario, asi que es idempotente tambien bajo carrera: dos
     * llamadas simultaneas dejan UNA fila y ninguna falla. Nunca pisa la fila existente.
     */
    void crearSiFalta(ConversacionRenasia conversacion);
}
