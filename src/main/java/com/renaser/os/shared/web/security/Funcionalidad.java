package com.renaser.os.shared.web.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara <b>a qué funcionalidad pertenece</b> este endpoint (spec 002, FR-009; contrato §1), como
 * {@link RequiresPermission} declara qué permiso exige. Si Soporte TI la apaga —para todos o para el rol de quien
 * llama—, el interceptor de {@code plataforma} responde 503 con el aviso, sin entrar al caso de uso.
 *
 * <p>Va en la clase (vale para todos sus handlers) o en el método (gana sobre la de la clase). El valor es una clave
 * del catálogo de {@code plataforma} ({@code "chat.grupos"}); {@code FuncionalidadDeclaradaTest} falla si un handler
 * no declara ninguna (ni {@link SinInterruptor}) o si la clave no existe.
 *
 * <p>Declara, no ejecuta: el controller sigue tonto (CLAUDE.MD §5.4.6).
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Funcionalidad {

    /** La clave del catálogo, por ejemplo {@code "chat.grupos"}. */
    String value();

    /**
     * {@code true} si, con la funcionalidad apagada, la respuesta no es el 503 del interceptor sino la que ya entiende
     * la app, y la decide el caso de uso preguntándole a {@code Interruptores}. Hoy, solo la voz del orbe: con
     * {@code ia.voz} apagada responde 204 y la app habla con la voz del teléfono (spec 002, D-267). La clave sigue
     * declarada: aparece en la consola y la verifica la prueba de arquitectura.
     */
    boolean laResuelveElCasoDeUso() default false;
}
