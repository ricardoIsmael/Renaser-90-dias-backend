package com.renaser.os.community.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La foto propia de un grupo (D-212): la que eligió el administrador o su mentor en vez de la tarjeta de
 * Renaser. Es de {@code community}, que guarda cuál es; la sirve {@code chat}, en la foto de la
 * conversación del grupo, solo a quien puede ver ese chat.
 */
public interface FotoPropiaDelGrupoFinder {

    /** Los bytes (JPEG) y desde cuándo; vacío si el grupo usa la foto de Renaser o no se pudo leer. */
    Optional<FotoPropia> fotoDe(UUID grupoId);

    /**
     * Desde cuándo tiene foto propia cada uno de estos grupos; los que usan la de Renaser no figuran. Una
     * sola consulta, para la lista de chats.
     */
    Map<UUID, Instant> cambiadasEn(Collection<UUID> grupos);

    /**
     * @param jpeg       la foto, ya preparada (cuadrada, JPEG)
     * @param cambiadaEn cuándo se eligió
     */
    record FotoPropia(byte[] jpeg, Instant cambiadaEn) {
    }
}
