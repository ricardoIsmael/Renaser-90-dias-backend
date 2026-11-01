package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado cuando Administración pasa a un aprendiz de una formación a otra (spec 022, Q1-A). {@code users} ya reinició su participación (día 0, sin
 * Día 1, sin grupo ni mentor) y guardó la foto de lo que fue; los demás módulos limpian lo suyo:
 * <ul>
 *   <li>{@code community}: lo saca de su grupo y lo entra al grupo de bienvenida de la formación nueva.</li>
 *   <li>{@code points}: sus puntos y su racha empiezan de cero (Q8-A) con un ajuste del ledger.</li>
 *   <li>las specs 023 y 024 suman lo suyo (ranking, hábitos) escuchando este mismo evento.</li>
 * </ul>
 *
 * @param celulaAnteriorId el grupo en el que estaba ({@code null} si no tenía)
 */
public record AprendizTrasladadoDeFormacionEvent(UserId aprendizId, Formacion desde, Formacion hacia, UUID celulaAnteriorId,
                                                 UserId trasladadoPor, Instant occurredAt) implements DomainEvent {
}
