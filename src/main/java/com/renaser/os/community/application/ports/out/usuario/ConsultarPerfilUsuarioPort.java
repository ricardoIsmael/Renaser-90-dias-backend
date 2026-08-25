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

    record PerfilUsuario(UserId id, String nombreCompleto, String avatarUrl) {
    }
}
