package com.renaser.os.shared.domain;

/**
 * La contraseña escrita para desbloquear la consola Soporte TI (o confirmar un cambio crítico) no es la de la cuenta
 * (spec 002, plan.md D6). Responde <b>401</b> con {@code codigo: "CONTRASENA_INCORRECTA"}: la app trata un 401 a secas
 * como sesión vencida, y con el código sabe que la sesión sigue viva y solo hay que reescribir la contraseña.
 */
public final class ContrasenaIncorrectaException extends RuntimeException {

    public ContrasenaIncorrectaException() {
        super("La contraseña no es correcta.");
    }
}
