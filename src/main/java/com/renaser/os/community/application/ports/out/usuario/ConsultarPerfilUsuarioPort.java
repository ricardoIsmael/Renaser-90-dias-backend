package com.renaser.os.community.application.ports.out.usuario;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Copia PROPIA de `community` sobre `usuarios` (tabla de `users`) para lo que
 * {@code users.api.UserSummaryFinder} todavia no expone: `avatar_url`. Autores de
 * publicaciones/comentarios, mentor de una celula y testimonios promovidos necesitan
 * mostrar avatar (wall/schema.ts:115-116, community/schema.ts:87) — sin este campo en la
 * API publica de `users`, la unica forma fiel de portar esas pantallas es leerlo aca,
 * mismo criterio que `rocks` con `participantes_programa`/`usuarios`
 * (docs/MODULO_COMMUNITY.md sec. 4: pedido para que `users.api.UserSummary` sume
 * `avatarUrl` y este puerto se pueda retirar).
 */
public interface ConsultarPerfilUsuarioPort {

    Optional<PerfilUsuario> porId(UserId id);

    /** Evita N+1 al enriquecer un feed/pagina completa de una sola pasada. */
    Map<UserId, PerfilUsuario> porIds(Collection<UserId> ids);

    /**
     * @param sobrenombre spec 046: el sobrenombre que reemplaza su nombre en el Muro, o {@code null}. Solo lo usa el Muro: el resto de
     *                    la Comunidad (grupos, chat, ranking) sigue con el nombre real.
     */
    record PerfilUsuario(UserId id, String nombreCompleto, String avatarUrl, String sobrenombre) {

        public PerfilUsuario(UserId id, String nombreCompleto, String avatarUrl) {
            this(id, nombreCompleto, avatarUrl, null);
        }

        /** Spec 046 (D3): cómo se le nombra en el Muro: su sobrenombre si tiene uno; si no, su nombre. */
        public String nombreEnElMuro() {
            return sobrenombre != null ? sobrenombre : nombreCompleto;
        }
    }
}
