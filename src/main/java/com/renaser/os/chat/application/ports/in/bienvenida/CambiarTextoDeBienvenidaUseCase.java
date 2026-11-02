package com.renaser.os.chat.application.ports.in.bienvenida;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

/**
 * Cambiar un mensaje de la bienvenida de una formación, o volver al original de esa formación (D-210, spec 035). Solo ADMIN y ALCHEMIST con
 * la cuenta activa. Cada cambio queda en la bitácora con quién y cuándo; lo que sale en la próxima
 * bienvenida es el último.
 */
public interface CambiarTextoDeBienvenidaUseCase {

    /**
     * Guardar el mismo texto que ya sale no cambia nada.
     *
     * @param clave la de {@code PiezaDeBienvenida}: SOPORTE_CON_LA_TARJETA, SOPORTE_FORMAL o GRUPO. Llega
     *              cruda a propósito: quien no puede cambiar la bienvenida recibe 403 antes que un 400
     * @throws IllegalArgumentException si la clave no existe o el texto no sirve ({@code TextoDeBienvenida})
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si no es ADMIN/ALCHEMIST activo
     */
    BienvenidaEditable cambiar(UserId actorId, Formacion formacion, String clave, String texto);

    /** Si ya salía el original, no cambia nada. */
    BienvenidaEditable volverAlOriginal(UserId actorId, Formacion formacion, String clave);
}
