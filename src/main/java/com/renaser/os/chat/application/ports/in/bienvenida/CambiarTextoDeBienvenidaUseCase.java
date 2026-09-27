package com.renaser.os.chat.application.ports.in.bienvenida;

import com.renaser.os.shared.domain.UserId;

/**
 * Cambiar un mensaje de la bienvenida, o volver al original del repo (D-210). Solo ADMIN y ALCHEMIST con
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
    BienvenidaEditable cambiar(UserId actorId, String clave, String texto);

    /** Si ya salía el original, no cambia nada. */
    BienvenidaEditable volverAlOriginal(UserId actorId, String clave);
}
