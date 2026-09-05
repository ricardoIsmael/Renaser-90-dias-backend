package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

/**
 * URL prefirmada para subir una foto o un audio de chat, via {@code AlmacenamientoPort}
 * (CLAUDE.MD sec. "STORAGE"). Mismo patron "upload-url -> PUT -> enviar mensaje" que ya usan
 * `community` (POST /wall/media/upload-url), `rocks`, `habits` y `onboarding`.
 *
 * <p>Por que existe: {@code EnviarMensajeUseCase} acepta {@code mediaBucket}/{@code mediaRuta}
 * desde el primer dia, pero `chat` era el UNICO modulo del backend sin endpoint de subida — no
 * habia de donde sacar esas dos referencias. El cliente movil lo tenia documentado como hueco
 * (`chatApi.ts`) y mostraba botones de foto y audio que no mandaban nada al servidor.
 *
 * <p>La autorizacion es la MISMA que la de enviar un mensaje: activo, la conversacion existe y
 * el actor participa en ella. Se comprueba aca y no solo al enviar porque la URL se firma ANTES
 * de que el mensaje exista: sin este chequeo, cualquiera con sesion podria firmar subidas contra
 * el prefijo de una conversacion ajena.
 */
public interface SolicitarUrlSubidaMediaChatUseCase {

    UrlSubidaMediaChat solicitarUrl(SolicitarUrlSubidaMediaChatCommand command);

    record SolicitarUrlSubidaMediaChatCommand(@NotNull UserId actorId,
                                               @NotNull ConversacionId conversacionId,
                                               @NotBlank String tipoContenido) {

        public SolicitarUrlSubidaMediaChatCommand {
            SelfValidating.validateConstructorArgs(SolicitarUrlSubidaMediaChatCommand.class, actorId,
                    conversacionId, tipoContenido);
        }
    }

    record UrlSubidaMediaChat(URI url, String bucket, String ruta) {
    }
}
