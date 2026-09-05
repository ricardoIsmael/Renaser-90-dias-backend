package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code tipoContenido} es el MIME del archivo que el cliente va a subir ("image/jpeg",
 * "audio/m4a"). Mismo nombre de campo que el equivalente del Muro
 * ({@code SolicitarUrlSubidaMediaRequest}) para que el cliente movil no tenga dos convenciones.
 */
public record SolicitarUrlSubidaMediaChatRequest(@NotBlank String tipoContenido) {
}
