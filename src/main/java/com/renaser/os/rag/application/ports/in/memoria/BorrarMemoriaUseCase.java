package com.renaser.os.rag.application.ports.in.memoria;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/** La persona borra lo que el acompanante recuerda de ella, desde su perfil (D-167). */
public interface BorrarMemoriaUseCase {

    /**
     * Ese recuerdo y el resumen de lo conversado, que podia nombrarlo.
     *
     * @throws java.util.NoSuchElementException si no existe o no es suyo
     */
    void borrarRecuerdo(UserId actorId, UUID recuerdoId);

    /** Todos sus recuerdos y el resumen. Lo conversado hasta ahora no vuelve a la memoria. */
    void borrarTodo(UserId actorId);
}
