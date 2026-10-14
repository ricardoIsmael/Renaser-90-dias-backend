package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;

/** Guarda el estado nuevo de una funcionalidad con bloqueo optimista (contrato §6: 409 si la versión cambió). */
public interface GuardarEstadoPort {

    /** La versión que le toca al próximo cambio: una secuencia global, así la mayor es la del estado completo. */
    long siguienteVersion();

    /**
     * Reemplaza la fila y sus excepciones por rol, en la transacción de quien llama.
     *
     * @param versionEsperada la versión que tenía la fila al leerla, 0 si no existía
     * @return {@code false} si otro cambio llegó antes (la fila ya no tiene esa versión): no se guardó nada
     */
    boolean guardar(EstadoDeFuncionalidad nuevo, long versionEsperada);
}
