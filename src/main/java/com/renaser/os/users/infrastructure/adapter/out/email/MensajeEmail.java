package com.renaser.os.users.infrastructure.adapter.out.email;

/**
 * Un correo ya redactado, listo para enviar: asunto y cuerpo HTML. Existe para que
 * {@link SmtpEnviarEmailAdapter} tenga un solo metodo de envio en vez de cuatro casi
 * identicos — el adaptador se ocupa del TRANSPORTE (SMTP, UTF-8, errores) y
 * {@link PlantillasEmail} de la REDACCION (SRP, CLAUDE.MD §5.4.8).
 *
 * <p>Vive en {@code infrastructure/adapter/out/email} y no en {@code domain}: el HTML es un
 * detalle de entrega, no una regla de negocio (CLAUDE.MD §5.1.2).
 */
record MensajeEmail(String asunto, String cuerpoHtml) {
}
