package com.renaser.os.notifications.application.services;

/**
 * El nombre de pila con que se saluda en el mensaje de un evento (spec 041): la primera palabra del nombre completo. Sin nombre, un saludo neutro, para que el
 * hueco de la plantilla no vaya vacio (Meta lo rechaza).
 */
final class NombreDePila {

    static final String SALUDO_NEUTRO = "participante";

    private NombreDePila() {
    }

    static String de(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return SALUDO_NEUTRO;
        }
        return nombreCompleto.strip().split("\\s+")[0];
    }
}
