package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Compartir una publicacion del Muro dentro de una conversacion de chat.
 *
 * <p><b>Por que existe como caso de uso del servidor y no como un mensaje que armaba el cliente.</b>
 * Es la correccion de un bug real: {@code handleShareToConversation} de la app pegaba la URL
 * firmada de S3 como TEXTO dentro del mensaje (linea {@code mensaje += "\n📷 Ver foto: " + foto}).
 * Esa URL trae {@code X-Amz-Expires=900} — <b>quince minutos</b> — asi que la foto compartida
 * moria a los quince minutos y el enlace roto quedaba guardado para siempre en una fila que no
 * caduca. Era exactamente lo que el javadoc de {@link MensajeEnriquecido} ya advertia: <i>"una URL
 * firmada vence y guardarla dejaria la foto en 403 para siempre"</i>.
 *
 * <p>Ahora el mensaje se guarda como <b>media de verdad</b> (bucket + ruta, los mismos que ya
 * usa cualquier foto de chat) y la URL se vuelve a firmar en cada lectura, en
 * {@code MensajeService.urlDeLectura}. La foto no caduca nunca y no se copia un solo byte de S3.
 *
 * <p><b>Solo entra el id de la publicacion, nada mas.</b> Ni el texto, ni el nombre del autor, ni
 * la URL: todo eso lo resuelve el servidor contra {@code community.api} y {@code users.api}. Es
 * la misma razon por la que {@code SubmitAccountRequestCommand} no tiene campo {@code role}
 * (CLAUDE.MD sec. 5.3.3) — lo que el cliente manda en un texto que queda persistido para siempre
 * no se puede auditar despues.
 */
public interface CompartirPublicacionUseCase {

    Mensaje compartir(CompartirPublicacionCommand command);

    /**
     * @param publicacionId la publicacion del Muro a compartir. {@link UUID} crudo y no un
     *                      {@code PublicacionId} de dominio porque ese tipo pertenece a
     *                      {@code community} y es interno suyo: entre modulos viaja el UUID, que
     *                      es lo que {@code PublicacionMuroFinder} recibe (D-41).
     */
    record CompartirPublicacionCommand(@NotNull UserId actorId, @NotNull ConversacionId conversacionId,
                                        @NotNull UUID publicacionId) {

        public CompartirPublicacionCommand {
            SelfValidating.validateConstructorArgs(CompartirPublicacionCommand.class, actorId, conversacionId,
                    publicacionId);
        }
    }
}
