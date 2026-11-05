package com.renaser.os.calendar.application.ports.out.meet;

/** Google rechazó el permiso permanente ({@code invalid_grant}, o 401 con un token recién renovado): se venció, se revocó o se cambió la contraseña. */
public final class CuentaGoogleRevocadaException extends RuntimeException {

    public CuentaGoogleRevocadaException(String motivo) {
        super(motivo);
    }
}
