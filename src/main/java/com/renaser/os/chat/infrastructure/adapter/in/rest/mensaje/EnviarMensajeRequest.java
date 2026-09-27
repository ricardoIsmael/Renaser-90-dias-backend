package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code type} en ingles (TEXT/IMAGE/AUDIO/VIDEO) — traducido en el controller. {@code SYSTEM} se
 * traduce igual pero responde 400: un mensaje de sistema lo escribe el programa, no una persona
 * ({@code Mensaje.escribir}, E-332).
 */
public record EnviarMensajeRequest(@NotBlank String type, String text, String mediaBucket, String mediaPath,
                                    String mediaMime, Integer mediaBytes, Short mediaDurationSeconds,
                                    String replyToId) {
}
