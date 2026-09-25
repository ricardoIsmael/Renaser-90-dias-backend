package com.renaser.os.rag.application.ports.in.propuesta;

import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que usa una herramienta de ESCRITURA del acompanante en lugar de escribir (fase 2, D-153):
 * deja la accion guardada como propuesta pendiente y la persona la confirma con un boton.
 *
 * <p><b>El modelo nunca ejecuta una escritura.</b> Solo puede llegar hasta aca. La ejecucion la
 * dispara {@link ResolverPropuestaUseCase#confirmar}, que llama la app cuando la persona toca
 * "Confirmar". Es lo que evita otro D-132 (un "Hola" que marco un habito).
 *
 * <p>Quien propone valida ANTES con las guardas reales del negocio, para no ofrecer un boton que
 * va a fallar; {@code confirmar} las vuelve a correr igual, porque entre proponer y confirmar
 * pasa el tiempo.
 */
public interface ProponerAccionUseCase {

    /**
     * @param invocacion la herramienta y sus argumentos tal como se van a ejecutar al confirmar
     * @param resumen    lo que ve la persona junto a los botones, en castellano y con el cambio
     *                   exacto ("Meditar: de 06:00 a 07:00 desde manana")
     */
    PropuestaCreada proponer(UserId actorId, InvocacionHerramienta invocacion, String resumen);

    record PropuestaCreada(UUID id, String resumen, Instant venceEn) {
    }
}
