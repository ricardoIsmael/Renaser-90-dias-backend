package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Las cuentas de prueba de «ver como rol» (spec 002, fase 2, US5; contrato fase-2.md §5; D-273): una por rol base
 * (los seis, nunca Soporte TI) y una por rol personalizado activo, creadas la primera vez que se piden y reparadas cada
 * vez (rol, nombre, activa). Sin contraseña: no se entra con ellas por el login. Con base aprendiz, además, el
 * programa activo a mitad del camino (día 45, que se vuelve a poner si el reloj la sacó del tramo 30–60) y el mentor y
 * la guía con su perfil de mentor.
 *
 * <p>Las crea el sistema, sin solicitud ni invitación: <b>no</b> publican {@code UsuarioRegistradoEvent} (que las
 * sumaría al chat de la comunidad, a un chat de soporte con el staff real y al grupo de bienvenida). Solo las pide la
 * consola Soporte TI (la plataforma), y este caso de uso vuelve a exigir que quien pide sea Soporte TI activo (403).
 */
public interface CuentasDePruebaUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si {@code soporteTi} no es Soporte TI activo (403)
     * @throws IllegalArgumentException si {@code rol} es Soporte TI (400)
     */
    CuentaDePrueba deRol(UserId soporteTi, UserRole rol);

    /**
     * La cuenta de prueba de un rol personalizado: su rol base y el rol personalizado asignado (después se publica
     * {@link RolPersonalizadoCambiadoEvent}, para que la regla de disponibilidad la vea enseguida).
     *
     * @throws java.util.NoSuchElementException si el rol no existe (404)
     * @throws IllegalArgumentException         si está desactivado (400)
     */
    CuentaDePrueba deRolPersonalizado(UserId soporteTi, UUID rolPersonalizadoId);
}
