package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;

/**
 * La escritura real que ejecuta una propuesta cuando la persona la confirma (fase 2, D-153).
 *
 * <p>Una herramienta de escritura tiene dos mitades: la que ve el modelo (propone, via
 * {@code ProponerAccionUseCase}) y esta, que solo llama {@code ResolverPropuestaUseCase.confirmar}.
 * Separadas a proposito: el modelo no tiene ningun camino hasta {@link #aplicar}.
 *
 * <p>{@link #aplicar} delega en el caso de uso de negocio de siempre, que vuelve a correr todas
 * sus guardas (plazos, cuota, obligatorios, dueno). Igual que las herramientas: un rechazo del
 * negocio vuelve como {@code Fallo} legible, nunca como excepcion.
 */
public interface AccionConfirmable {

    /** El nombre de la herramienta cuya propuesta ejecuta; es la clave con que se guardo. */
    String herramienta();

    ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion);
}
