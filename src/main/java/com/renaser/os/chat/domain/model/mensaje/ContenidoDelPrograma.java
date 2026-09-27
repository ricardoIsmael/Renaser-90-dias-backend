package com.renaser.os.chat.domain.model.mensaje;

/**
 * Lo que dice un mensaje del programa ({@link Mensaje#delPrograma}): un texto o una imagen que subió
 * el servidor (la tarjeta de bienvenida). Las reglas de contenido las valida {@code Mensaje}.
 */
public record ContenidoDelPrograma(String texto, String mediaBucket, String mediaRuta, String mediaMime,
                                   Integer mediaBytes) {

    public static ContenidoDelPrograma texto(String texto) {
        return new ContenidoDelPrograma(texto, null, null, null, null);
    }

    /** Una imagen del depósito del chat, ya subida bajo el prefijo de la conversación. */
    public static ContenidoDelPrograma imagen(String ruta, String mime, int bytes) {
        return new ContenidoDelPrograma(null, Mensaje.BUCKET_DEFAULT, ruta, mime, bytes);
    }
}
