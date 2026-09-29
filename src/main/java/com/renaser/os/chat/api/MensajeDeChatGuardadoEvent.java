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
 *
 * <p><b>{@code soloPara}</b> (D-223): si viene, el aviso es SOLO para esa persona, aunque el chat tenga a
 * más gente. Lo usa la tarjeta diaria del semáforo: sale todas las noches en el soporte de cada aprendiz,
 * y sin esto cada Admin y Alquimista recibiría un push por cada aprendiz del padrón. La persona igual
 * tiene que estar entre los destinatarios que resuelve {@link AvisosDeMensajesFinder} (no abre un aviso a
 * quien no ve el chat). {@code null} = todos, como siempre; un evento viejo del outbox, guardado antes de
 * que existiera el campo, llega con {@code null}.
 */
public record MensajeDeChatGuardadoEvent(UUID mensajeId, UUID conversacionId, UUID soloPara) {

    public MensajeDeChatGuardadoEvent {
        Objects.requireNonNull(mensajeId, "mensajeId es obligatorio");
        Objects.requireNonNull(conversacionId, "conversacionId es obligatorio");
    }

    /** El aviso de siempre: a todos los que ven el chat, menos el autor y quien lo tiene abierto. */
    public MensajeDeChatGuardadoEvent(UUID mensajeId, UUID conversacionId) {
        this(mensajeId, conversacionId, null);
    }

    /** Si el aviso de este mensaje le corresponde a esa persona (ya filtrada por la regla del chat). */
    public boolean alcanzaA(UUID usuarioId) {
        return soloPara == null || soloPara.equals(usuarioId);
    }
}
