package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * La foto de cada integrante de un grupo en la app (D-206, decisión del dueño del 2026-09-27): su
 * tarjeta de Canva con su primer nombre, que dibuja y sirve {@code chat}, en el chat del grupo.
 * {@code community} la pide para ponerla en {@code /me/cells} y {@code /me/cells/{id}/members}, que es
 * lo que arma la lista de integrantes de la info del chat.
 *
 * <p><b>Por qué un contrato que implementa chat y no una consulta de community.</b> La ruta, la
 * conversación, la regla de quién puede ver la foto y el modo (tarjeta siempre, o solo para quien no
 * subió foto) son de {@code chat}, y community no puede importar chat: chat ya importa
 * {@code community.api}, así que la dependencia inversa cerraría un ciclo. Mismo criterio que
 * {@link ReferenciasExternasDeMediaDelMuro}.
 */
public interface FotosDeIntegrantesDelGrupo {

    /**
     * La ruta de la tarjeta de cada uno de estos integrantes del grupo que se muestra con ella, relativa a
     * la API y pedida con la sesión. Quien no figura se muestra con su foto subida o sus iniciales: el
     * grupo todavía no tiene chat, o el modo del servidor es «su foto si la subió» y la subió. Una sola
     * consulta por grupo, sin importar cuántos sean.
     */
    Map<UserId, String> rutasDeLasFotos(UUID grupoId, Collection<UserId> integrantes);
}
