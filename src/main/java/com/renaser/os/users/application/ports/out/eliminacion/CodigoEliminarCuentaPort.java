package com.renaser.os.users.application.ports.out.eliminacion;

import java.time.Duration;

/**
 * Codigo de 6 digitos que confirma que la persona quiere eliminar su cuenta (D-243): desde la pagina
 * web (sin sesion) y desde la app cuando la cuenta no tiene contraseña (entra con Google o Apple).
 *
 * <p>Puerto propio y no el del reset ni el del alta, por lo mismo que esos dos estan separados entre
 * si ({@code CodigoResetContrasenaPort}): si compartieran clave, un codigo pedido para recuperar la
 * contraseña serviria para borrar la cuenta. Keyed por correo, como los otros dos.
 */
public interface CodigoEliminarCuentaPort {

    /** Codigo nuevo para {@code email}; reemplaza cualquier anterior y su contador de intentos. */
    String generarCodigo(String email, Duration vigencia);

    /** Compara y, si coincide, lo consume. Al llegar a {@code maxIntentos} fallidos, lo borra. */
    boolean verificarCodigo(String email, String codigo, int maxIntentos);
}
