package com.renaser.os.chat.application.ports.in.bienvenida;

import com.renaser.os.shared.domain.Formacion;

import java.time.Instant;
import java.util.Optional;

/**
 * La imagen que una formación tiene en el chat (spec 035): la de su comunidad general y la de sus grupos sin foto propia.
 *
 * <p>Es la propia de la formación o, si no tiene, la vigente de la Escuela; si tampoco, no hay imagen de formación y la app usa la suya.
 * El que consulta ya sabe que la persona puede ver la conversación: acá no se autoriza nada.
 */
public interface ConsultarImagenDeLaFormacionUseCase {

    /** Vacío si la formación no tiene ninguna imagen subida (ni propia ni heredada). */
    Optional<ImagenDeLaFormacionVigente> vigente(Formacion formacion);

    /**
     * La imagen lista para servir (JPEG cuadrado).
     *
     * @return vacío si no hay imagen vigente, o si la guardada ya no abre (se avisa en el log y la app usa la suya)
     */
    Optional<ImagenServida> servir(Formacion formacion);

    /** @param desde cuándo se eligió la imagen que sale: va en la ruta de la foto ({@code ?v=}) para que el teléfono baje la nueva */
    record ImagenDeLaFormacionVigente(String ruta, Instant desde) {
    }

    /** @param huella cambia si y solo si cambia la imagen (el ETag) */
    record ImagenServida(byte[] jpeg, String huella) {
    }
}
