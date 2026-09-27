package com.renaser.os.community.application.ports.out.acompanamiento;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * La marca de bienvenida de grupo, {@code asignaciones_celula.bienvenida_enviada_en} (V71, D-191).
 * Fuera del agregado {@code AsignacionCelula} a propósito: no es parte de la regla de pertenencia,
 * y así el UPDATE de una asignación por JPA no puede pisarla.
 */
public interface MarcaDeBienvenidaEnGrupoPort {

    /** De estas asignaciones, cuáles siguen sin marca. En UNA consulta. */
    Set<UUID> sinBienvenida(Collection<UUID> asignacionIds);

    /** @return {@code true} si la marcó ahora; {@code false} si ya estaba marcada o está cerrada */
    boolean marcar(UUID asignacionId, Instant instante);
}
