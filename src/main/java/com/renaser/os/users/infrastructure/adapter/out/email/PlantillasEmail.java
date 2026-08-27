package com.renaser.os.users.infrastructure.adapter.out.email;

/**
 * Redaccion de los cuatro correos transaccionales de {@code users}. Separada del transporte
 * ({@link SmtpEnviarEmailAdapter}) por SRP: cambiar el texto de un correo no deberia obligar a
 * tocar la clase que habla SMTP, ni al reves.
 *
 * <p><b>Reutilizacion:</b> los cuatro correos comparten un unico envoltorio ({@link #envolver})
 * y solo dos formas de cuerpo — con boton (link de un solo uso) o con dato a la vista (codigo /
 * contrasena temporal). Agregar un quinto correo es elegir una de las dos, no copiar HTML.
 *
 * <p><b>Sobre inyeccion de HTML:</b> todo lo que se interpola aca lo genera el propio backend
 * (tokens opacos, codigos de 6 digitos, contrasenas temporales, URLs de configuracion). Ningun
 * texto escrito por una persona llega a estas plantillas, asi que no hay superficie de HTML
 * injection. Si algun dia se interpola un nombre o un motivo, hay que escaparlo primero.
 *
 * <p><b>Alcance:</b> el texto es funcional y deliberadamente sobrio. La copia definitiva es
 * decision de producto, no del backend (CLAUDE.MD §0.6: no se inventan reglas de negocio) —
 * cambiarla despues no toca ni el puerto ni el caso de uso.
 */
final class PlantillasEmail {

    private PlantillasEmail() {
    }

    static MensajeEmail resetContrasena(String enlace) {
        return new MensajeEmail("Recupera tu contrasena de Renaser",
                conBoton("Recupera tu contrasena",
                        "Recibimos un pedido para restablecer la contrasena de tu cuenta. "
                                + "Si no fuiste vos, podes ignorar este correo.",
                        "Elegir una contrasena nueva", enlace));
    }

    static MensajeEmail activacionCuenta(String enlace) {
        return new MensajeEmail("Tu cuenta de Renaser fue aprobada",
                conBoton("Tu cuenta fue aprobada",
                        "Ya podes activarla. Elegi tu contrasena y entra a la aplicacion.",
                        "Activar mi cuenta", enlace));
    }

    static MensajeEmail codigoVerificacion(String codigo) {
        return new MensajeEmail("Tu codigo de verificacion de Renaser",
                conDato("Verifica tu correo",
                        "Escribi este codigo en la aplicacion para confirmar que esta casilla es tuya.",
                        codigo, "El codigo vence en unos minutos y sirve una sola vez."));
    }

    static MensajeEmail invitacionStaff(String contrasenaTemporal) {
        return new MensajeEmail("Tu acceso a Renaser",
                conDato("Te dimos acceso a Renaser",
                        "Entra con tu correo y esta contrasena temporal.",
                        contrasenaTemporal, "Cambiala apenas entres."));
    }

    /** Link de un solo uso hacia el frontend. La URL base es configuracion, nunca se hardcodea. */
    static String linkConToken(String urlBase, String token) {
        return urlBase + "?token=" + token;
    }

    private static String conBoton(String titulo, String parrafo, String textoBoton, String enlace) {
        return envolver(titulo, """
                <p style="margin:0 0 24px">%s</p>
                <a href="%s" style="display:inline-block;padding:12px 24px;border-radius:8px;\
                background:#111;color:#fff;text-decoration:none;font-weight:500">%s</a>
                <p style="margin:24px 0 0;font-size:13px;color:#666">\
                Si el boton no funciona, copia este enlace:<br>%s</p>"""
                .formatted(parrafo, enlace, textoBoton, enlace));
    }

    private static String conDato(String titulo, String parrafo, String dato, String nota) {
        return envolver(titulo, """
                <p style="margin:0 0 24px">%s</p>
                <p style="margin:0;font-size:30px;font-weight:600;letter-spacing:6px">%s</p>
                <p style="margin:24px 0 0;font-size:13px;color:#666">%s</p>"""
                .formatted(parrafo, dato, nota));
    }

    /** Envoltorio comun: mismo encabezado y mismo ancho para los cuatro correos. */
    private static String envolver(String titulo, String contenido) {
        return """
                <!doctype html><html lang="es"><body style="margin:0;background:#f5f5f4">
                <div style="max-width:520px;margin:0 auto;padding:40px 24px;font-family:\
                -apple-system,Segoe UI,Roboto,sans-serif;color:#111;line-height:1.5">
                <p style="margin:0 0 32px;letter-spacing:8px;font-size:15px">RENASER</p>
                <h1 style="margin:0 0 16px;font-size:22px;font-weight:500">%s</h1>
                %s
                </div></body></html>""".formatted(titulo, contenido);
    }
}
