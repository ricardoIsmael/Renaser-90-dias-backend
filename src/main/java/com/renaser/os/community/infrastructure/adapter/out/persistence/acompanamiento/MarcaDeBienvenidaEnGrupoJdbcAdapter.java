package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import com.renaser.os.community.application.ports.out.acompanamiento.MarcaDeBienvenidaEnGrupoPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * {@code asignaciones_celula.bienvenida_enviada_en} con {@link JdbcClient} (V71, D-191): la columna no
 * está mapeada en {@code AsignacionCelulaJpaEntity}, así que guardar una asignación no la pisa.
 *
 * <p>El UPDATE lleva {@code bienvenida_enviada_en IS NULL}: si dos entregas del evento se cruzan, la
 * segunda espera el lock de la fila, la encuentra marcada y afecta cero filas.
 */
@Component
class MarcaDeBienvenidaEnGrupoJdbcAdapter implements MarcaDeBienvenidaEnGrupoPort {

    private final JdbcClient jdbcClient;

    MarcaDeBienvenidaEnGrupoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<UUID> sinBienvenida(Collection<UUID> asignacionIds) {
        if (asignacionIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbcClient.sql("SELECT id FROM renaser.asignaciones_celula "
                        + "WHERE id IN (:ids) AND bienvenida_enviada_en IS NULL")
                .param("ids", asignacionIds)
                .query(UUID.class)
                .list());
    }

    @Override
    public boolean marcar(UUID asignacionId, Instant instante) {
        return jdbcClient.sql("UPDATE renaser.asignaciones_celula SET bienvenida_enviada_en = :en "
                        + "WHERE id = :id AND funcion = 'APRENDIZ' AND fin IS NULL AND bienvenida_enviada_en IS NULL")
                .param("en", Timestamp.from(instante))
                .param("id", asignacionId)
                .update() == 1;
    }
}
