package com.renaser.os.shared.web.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara que este endpoint se sirve <b>sin cuenta</b>. Se usa solo donde exigir una cuenta
 * seria una contradiccion: el alta, el login, la verificacion del correo y el reset de
 * contrasena ocurren <i>antes</i> de que exista una sesion.
 *
 * <p><b>No es el default ni una salida facil.</b> Un endpoint marcado publico por comodidad
 * es un agujero permanente: cuando {@code SecurityConfig} pase de {@code permitAll()} a
 * {@code authenticated()} (fase 4, {@code docs/MODULO_AUTH.md} §9), esta anotacion es la
 * lista de excepciones — lo que quede marcado aca sigue abierto para siempre. Si no se sabe
 * que exige un endpoint, se deja sin anotar y en la lista de exclusion del test, no se marca
 * publico.
 *
 * <p>Declara, no ejecuta: igual que {@link RequiresPermission}, hoy no hay filtro detras.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {

    /**
     * Por que este endpoint puede vivir sin cuenta. Obligatorio en la practica: un publico
     * sin justificacion es el que nadie se anima a cerrar despues.
     */
    String value();
}
