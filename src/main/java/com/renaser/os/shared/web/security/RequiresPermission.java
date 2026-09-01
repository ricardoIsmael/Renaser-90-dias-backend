package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.Permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara <b>que permiso</b> exige este endpoint (CLAUDE.MD §5.3.4). Nunca que rol: la
 * lista de roles repartida por los endpoints es justamente lo que obliga a revisarlos uno
 * por uno cuando aparece un rol nuevo.
 *
 * <p><b>Declara, no ejecuta.</b> Hoy no hay ningun filtro ni interceptor detras de esta
 * anotacion: la autorizacion real la siguen haciendo los guards dentro de los servicios
 * ({@code requireAdminActivo}, {@code requireActorPuedePublicar}, {@code RequireActiveUserGuard}).
 * Conectarla es la fase 4 de {@code docs/MODULO_AUTH.md} §9, junto con el paso de
 * {@code permitAll()} a {@code authenticated()} en {@code SecurityConfig}. Lo que existe
 * hoy es el inventario y el test que lo mantiene completo
 * ({@code EndpointAuthorizationDeclarationTest}).
 *
 * <p>El controller sigue tonto: la anotacion es metadato sobre el metodo, no codigo que el
 * controller ejecute (§5.4.6).
 *
 * <p>Si el endpoint no exige nada porque se usa antes de tener cuenta, va
 * {@link PublicEndpoint} en su lugar. Las dos a la vez es un error y el test lo rechaza.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * El permiso que el actor tiene que tener. Uno solo, a proposito: si dos roles distintos
     * pueden hacer la misma operacion, eso es un permiso que los dos tienen — lo resuelve la
     * matriz rol -> permiso, no una lista en el endpoint.
     */
    Permission value();

    /**
     * Chequeos de <b>relacion</b> que el endpoint sigue delegando al caso de uso porque no
     * son preguntas de rol: propiedad del recurso ({@code requireSelf}), mentor asignado
     * ({@code requireMentorScope}), liderazgo de celula, membresia de una conversacion.
     *
     * <p>Documentar esto aca es lo que evita que la fase 4 lea "ya esta cubierto por el
     * permiso" y borre un guard que seguia haciendo falta.
     */
    String scope() default "";
}
