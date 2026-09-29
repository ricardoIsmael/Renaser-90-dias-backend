package com.renaser.os.chat.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Se guardó un mensaje en un chat: de una persona o del programa (D-221). Lo escucha
 * {@code notifications} para avisar a los demás participantes con un push.
 *
 * <p>Lleva solo los ids a propósito. Quién recibe el aviso, cómo se llama el chat y cuántos mensajes
 * lleva sin leer cada uno se preguntan AL ENTREGAR ({@link AvisosDeMensajesFinder}), no al publicar:
 * así un reintento del outbox no avisa a quien ya abrió el chat ni a quien ya no pertenece al grupo,
 * y el evento no carga la lista entera de la comunidad (cientos de ids) en cada mensaje.
 */
public record MensajeDeChatGuardadoEvent(UUID mensajeId, UUID conversacionId) {

    public MensajeDeChatGuardadoEvent {
        Objects.requireNonNull(mensajeId, "mensajeId es obligatorio");
        Objects.requireNonNull(conversacionId, "conversacionId es obligatorio");
    }
}
