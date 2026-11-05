package com.renaser.os.calendar.application.ports.in.meet;

import com.renaser.os.shared.domain.UserId;

/** Conectar, desconectar y ver la cuenta de Google que organiza las salas de Meet (spec 040, FR-010, FR-015). Solo ADMIN y ALCHEMIST, salvo el regreso de Google. */
public interface CuentaDeGoogleUseCase {

    /** El resultado de volver de Google: {@code mensaje} es la frase que se le muestra a la persona en la página de regreso. */
    record ResultadoDeLaConexion(boolean conectada, String mensaje) {
    }

    /** La dirección de Google a la que hay que mandar a la persona. 409 si el servidor no tiene las credenciales. */
    String iniciarConexion(UserId actor);

    /**
     * Lo que Google devuelve al navegador, sin sesión: la identidad sale del {@code state} (de un solo uso, 15 minutos). Nunca lanza: cualquier
     * problema vuelve como {@code conectada = false} con su frase.
     */
    ResultadoDeLaConexion completarConexion(String codigo, String state, String errorDeGoogle);

    /** Revoca el permiso en Google y lo borra. Los enlaces ya creados siguen existiendo. */
    void desconectar(UserId actor);

    EstadoDeLaCuentaVista estado(UserId actor);
}
