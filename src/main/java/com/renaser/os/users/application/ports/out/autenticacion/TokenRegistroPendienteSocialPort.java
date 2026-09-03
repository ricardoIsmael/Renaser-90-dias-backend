package com.renaser.os.users.application.ports.out.autenticacion;

import java.time.Duration;
import java.util.Optional;

/**
 * Token de un solo uso que retiene una {@link RegistroPendienteSocial} mientras la app muestra
 * el formulario de confirmacion del alta social (docs/MODULO_AUTH.md §6.10, D-65). Mismo patron
 * exacto que {@link TokenResetContrasenaPort}/{@link TokenVerificacionEmailPort}: vive solo en
 * Redis con vencimiento propio — la BD esta congelada (D-40) y ademas un registro efimero no
 * tiene sentido como tabla, Redis lo expira solo, sin cron de purga.
 */
public interface TokenRegistroPendienteSocialPort {

    /**
     * Genera un token opaco de alta entropia (aleatorio, nunca derivado del email ni del sujeto
     * del proveedor) y guarda el registro con el vencimiento dado.
     */
    String generar(RegistroPendienteSocial registro, Duration vigencia);

    /**
     * Busca el registro y lo borra en la MISMA operacion atomica (equivalente a {@code GETDEL}):
     * el `code` de OAuth que dio origen a este registro ya se gasto al llegar aca, asi que este
     * token es la unica prueba de identidad que queda y dos requests casi simultaneas con el
     * mismo token nunca pueden las dos tener exito. Vacio si el token no existe, ya vencio, o ya
     * se consumio antes.
     */
    Optional<RegistroPendienteSocial> consumir(String token);
}
