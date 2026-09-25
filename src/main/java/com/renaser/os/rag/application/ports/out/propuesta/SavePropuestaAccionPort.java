package com.renaser.os.rag.application.ports.out.propuesta;

import com.renaser.os.rag.domain.model.propuesta.PropuestaAccion;

/** Escritura de las propuestas del acompanante (D-153). */
public interface SavePropuestaAccionPort {

    /**
     * Inserta o actualiza, con control de concurrencia optimista sobre {@code version}: cada llamada
     * es su propia transaccion corta, y la escritura queda confirmada al volver.
     *
     * @return la propuesta tal como quedo guardada, con su {@code version} nueva
     * @throws PropuestaModificadaEnParaleloException si otra escritura la cambio despues de leerla
     */
    PropuestaAccion save(PropuestaAccion propuesta);
}
