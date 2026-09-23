package com.renaser.os.rag.application.ports.in.propuesta;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Lo que dispara la app cuando la persona toca un boton de una propuesta (fase 2, D-153).
 *
 * <p>{@link #confirmar} ejecuta la accion <b>una sola vez</b>: una segunda confirmacion (doble
 * toque, reintento de red) devuelve el mismo resultado sin volver a ejecutar. Solo el dueno de la
 * propuesta puede resolverla, y una propuesta vencida o cancelada ya no se puede confirmar.
 */
public interface ResolverPropuestaUseCase {

    ResultadoHerramienta confirmar(UserId actorId, UUID propuestaId);

    void cancelar(UserId actorId, UUID propuestaId);
}
