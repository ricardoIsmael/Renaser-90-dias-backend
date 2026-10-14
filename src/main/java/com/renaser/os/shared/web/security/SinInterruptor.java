package com.renaser.os.shared.web.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Este endpoint <b>no tiene interruptor</b> (spec 002, FR-004, «anti-bloqueo»): el login, la sesión, la lectura de los
 * interruptores, la consola Soporte TI y lo que la app necesita para arrancar. Ningún cambio de estado puede dejar a
 * Soporte TI sin acceso ni a la app sin poder entrar.
 *
 * <p>No es el valor por defecto ni una salida fácil: un endpoint marcado así no se puede apagar nunca. Si se duda, va
 * {@link Funcionalidad} con la clave que corresponda. El motivo es obligatorio.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SinInterruptor {

    /** Por qué este endpoint no se puede apagar. */
    String motivo();
}
