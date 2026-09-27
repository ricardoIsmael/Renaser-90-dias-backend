package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.FotoDeIntegrantes;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las fotos del chat que son la tarjeta de Canva con un primer nombre (decisiones del dueño del
 * 2026-09-27):
 * <ul>
 *   <li><b>La del chat de soporte</b> (D-205): la tarjeta con el primer nombre de su aprendiz.</li>
 *   <li><b>La de un grupo con foto propia</b> (D-212): la que eligió el administrador o su mentor, que
 *       guarda {@code community} ({@code community.api.FotoPropiaDelGrupoFinder}). Sin foto propia el
 *       grupo usa la tarjeta sin nombre que trae la app.</li>
 *   <li><b>La de cada integrante</b> de un grupo o de un soporte (D-206): su tarjeta con su primer
 *       nombre, para la lista de integrantes de la info del chat. Si se la muestra aunque haya subido
 *       una foto lo decide el modo del servidor ({@link FotoDeIntegrantes}).</li>
 * </ul>
 * La comunidad y los 1 a 1 no tienen tarjetas: la comunidad usa el fénix y un 1 a 1 la foto o las
 * iniciales de la persona.
 *
 * <p>Las ve quien puede ver la conversación, con la misma regla que el resto del chat
 * ({@link AutorizarAccesoAConversacionUseCase}).
 */
public interface VerFotosDelChatUseCase {

    /**
     * La foto propia de una conversación: en un soporte, la tarjeta de su aprendiz; en un grupo, la foto
     * que le eligieron (D-212).
     *
     * @throws java.util.NoSuchElementException si la conversación no existe, es la comunidad o un 1 a 1,
     *                                          o es un grupo que usa la foto de Renaser (404)
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si quien pide no puede verla o su
     *                                                             cuenta está suspendida (403)
     * <blockquote><b>Corregido 2026-09-27 (D-212).</b> Se llamaba {@code fotoDelSoporte} y un grupo daba
     * 404 siempre.</blockquote>
     */
    FotoDelChat fotoDeLaConversacion(UserId actorId, ConversacionId conversacionId);

    /**
     * La tarjeta de un integrante de un grupo o de un soporte. Es integrante quien puede ver esa
     * conversación con la misma regla que el resto del chat: en un grupo, su pertenencia vigente; en
     * un soporte, la aprendiz y el staff que participa. La sirve en cualquier modo: el modo decide a
     * quién se le manda la ruta, no qué tarjetas existen.
     *
     * @throws java.util.NoSuchElementException si la conversación no existe, no es un grupo ni un
     *                                          soporte, o esa persona no es integrante (404)
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si quien pide no puede verla o su
     *                                                             cuenta está suspendida (403)
     */
    FotoDelChat fotoDeIntegrante(UserId actorId, ConversacionId conversacionId, UserId integranteId);

    /**
     * Dónde y a quiénes: el chat del grupo, que es donde se sirven las tarjetas de su gente, y cuáles de
     * estos integrantes se muestran con su tarjeta según el modo del servidor ({@link FotoDeIntegrantes}):
     * con {@code TARJETA}, todos; con {@code FOTO_SUBIDA}, los que no subieron foto. Vacío si el grupo no
     * tiene chat.
     */
    Optional<TarjetasDelGrupo> tarjetasDelGrupo(UUID grupoId, Collection<UserId> integrantes);

    /**
     * @param jpeg   la tarjeta; es compartida entre pedidos, así que nadie la modifica
     * @param huella cambia si y solo si cambia la imagen (el ETag)
     */
    record FotoDelChat(byte[] jpeg, String huella) {
    }

    /**
     * @param chat       la conversación del grupo
     * @param conTarjeta los integrantes que se muestran con su tarjeta, en el orden en que se pidieron
     */
    record TarjetasDelGrupo(ConversacionId chat, List<UserId> conTarjeta) {
    }
}
