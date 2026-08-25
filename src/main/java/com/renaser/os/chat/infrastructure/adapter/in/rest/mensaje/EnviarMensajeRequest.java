package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import jakarta.validation.constraints.NotBlank;

/** {@code type} en ingles (TEXT/IMAGE/AUDIO/VIDEO/SYSTEM) — traducido en el controller. */
public record EnviarMensajeRequest(@NotBlank String type, String text, String mediaBucket, String mediaPath,
                                    String mediaMime, Integer mediaBytes, Short mediaDurationSeconds,
                                    String replyToId) {
}
