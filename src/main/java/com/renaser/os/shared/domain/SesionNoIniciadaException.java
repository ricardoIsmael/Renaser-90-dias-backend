package com.renaser.os.shared.domain;

/** No hay sesion activa (cookie ausente, vencida, o ya cerrada). 401, no 403. */
public class SesionNoIniciadaException extends RuntimeException {

    public SesionNoIniciadaException() {
        super("No hay una sesion activa");
    }
}
