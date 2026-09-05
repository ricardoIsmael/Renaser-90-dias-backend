package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.application.ports.in.mensaje.SolicitarUrlSubidaMediaChatUseCase.UrlSubidaMediaChat;

/**
 * {@code bucket} y {@code ruta} son exactamente lo que el cliente tiene que devolver despues en
 * {@code EnviarMensajeRequest.mediaBucket}/{@code mediaPath}. Misma forma que la respuesta del
 * Muro, para que el cliente reuse el mismo codigo de subida.
 */
public record UrlSubidaMediaChatResponse(String uploadUrl, String bucket, String ruta) {

    public static UrlSubidaMediaChatResponse from(UrlSubidaMediaChat url) {
        return new UrlSubidaMediaChatResponse(url.url().toString(), url.bucket(), url.ruta());
    }
}
