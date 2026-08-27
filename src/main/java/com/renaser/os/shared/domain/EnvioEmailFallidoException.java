package com.renaser.os.shared.domain;

/**
 * El proveedor de correo rechazo el envio o no respondio. Se lanza en vez de tragarse el fallo
 * porque un correo que no sale deja al sistema en un estado en el que nadie se entera: el codigo
 * de verificacion nunca llega y la persona espera, o la cuenta queda aprobada sin que su dueño
 * lo sepa ni tenga como fijar su contrasena.
 *
 * <p>Al propagarse desde {@code ApproveAccountRequestUseCase} —que es {@code @Transactional}—
 * revierte la aprobacion entera, que es el estado consistente: el admin ve el error y reintenta,
 * en lugar de una cuenta aprobada e inalcanzable.
 *
 * <p>El mensaje no expone nada del proveedor (host, credenciales, motivo SMTP): eso va al log,
 * no a la respuesta HTTP.
 */
public class EnvioEmailFallidoException extends RuntimeException {

    public EnvioEmailFallidoException(Throwable causa) {
        super("No pudimos enviar el correo. Intenta de nuevo en unos minutos.", causa);
    }
}
