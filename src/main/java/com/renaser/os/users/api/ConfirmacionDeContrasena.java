package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Si la contraseña escrita es la de esa cuenta (spec 002, D-267). La usa la consola Soporte TI para desbloquearse y
 * para confirmar un cambio crítico, sin conocer las credenciales ni el cifrado de {@code users}.
 *
 * <p>Una cuenta que no existe, que no está activa o que entra solo con Google o Apple (sin contraseña) responde
 * {@code false}: la consola no se puede abrir sin contraseña.
 */
public interface ConfirmacionDeContrasena {

    boolean esSuContrasena(UserId cuenta, String contrasena);
}
