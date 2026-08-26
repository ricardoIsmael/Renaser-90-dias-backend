package com.renaser.os.shared.web.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reemplazo progresivo de {@code @RequestHeader("X-Actor-Id") String actorId} (fase 4,
 * docs/MODULO_AUTH.md §8). Un controller que use {@code @ActorAutenticado UserId actorId}
 * recibe directo el {@link com.renaser.os.shared.domain.UserId} resuelto por
 * {@link ActorAutenticadoArgumentResolver} — sesion primero, header como respaldo mientras
 * dura la migracion de los 54 controllers existentes.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ActorAutenticado {
}
