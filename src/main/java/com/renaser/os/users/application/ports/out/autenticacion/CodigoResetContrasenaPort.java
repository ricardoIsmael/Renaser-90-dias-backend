package com.renaser.os.users.application.ports.out.autenticacion;

import java.time.Duration;

/**
 * Codigo de 6 digitos para recuperar la contrasena desde la app, sin salir a un navegador
 * (2026-09-04, D-102). Mismo contrato que {@link CodigoVerificacionEmailPort} — es el mismo
 * patron del alta, pedido asi por el dueno del producto: "lo mismo con OTP, verifico el OTP y
 * pongo la nueva contrasena" — pero un puerto aparte a proposito: el codigo de alta y el de
 * recuperacion NO pueden compartir la misma clave de Redis, porque entonces pedir uno
 * invalidaria al otro y un codigo emitido para "verifica tu correo" serviria para cambiar la
 * contrasena de una cuenta existente.
 *
 * <p>Keyed por EMAIL, igual que el de alta: es lo unico que la persona sabe en ese momento.
 * Quien lo verifica todavia no tiene sesion. El limite de intentos es el mismo criterio OWASP
 * del puerto de alta (ver ahi).
 */
public interface CodigoResetContrasenaPort {

    /**
     * Genera un codigo numerico nuevo para {@code email} con el vencimiento dado, reemplazando
     * cualquier codigo anterior y su contador de intentos: nunca hay dos codigos vivos para la
     * misma casilla.
     */
    String generarCodigo(String email, Duration vigencia);

    /**
     * Compara y, si coincide, consume (un solo uso). Si no coincide cuenta el intento fallido y
     * al llegar a {@code maxIntentos} borra el codigo entero para forzar pedir uno nuevo.
     */
    boolean verificarCodigo(String email, String codigo, int maxIntentos);
}
