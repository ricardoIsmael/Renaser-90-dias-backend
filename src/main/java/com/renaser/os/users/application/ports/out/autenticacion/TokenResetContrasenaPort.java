package com.renaser.os.users.application.ports.out.autenticacion;

import com.renaser.os.shared.domain.UserId;

import java.time.Duration;
import java.util.Optional;

/**
 * Token de un solo uso para resetear la contrasena. Vive solo en Redis con vencimiento propio
 * (docs/MODULO_AUTH.md §2.2): la BD esta congelada (D-40) y ademas un token efimero no tiene
 * sentido como tabla — Redis lo expira solo, sin cron de purga.
 */
public interface TokenResetContrasenaPort {

    /**
     * Genera un token opaco de alta entropia (aleatorio, nunca derivado del email ni del reloj)
     * y lo guarda con el vencimiento dado. Cada llamada emite un token nuevo: pedir un segundo
     * reset no invalida el primero por si solo, cada uno vence o se consume por su cuenta.
     */
    String generar(UserId usuarioId, Duration vigencia);

    /**
     * Busca el token y lo borra en la MISMA operacion atomica (equivalente a {@code GETDEL}):
     * dos requests casi simultaneas con el mismo token nunca pueden las dos tener exito. Vacio
     * si el token no existe, ya vencio, o ya se consumio antes.
     */
    Optional<UserId> consumir(String token);
}
