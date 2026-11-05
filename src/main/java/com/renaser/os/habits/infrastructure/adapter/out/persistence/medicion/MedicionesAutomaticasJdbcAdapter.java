package com.renaser.os.habits.infrastructure.adapter.out.persistence.medicion;

import com.renaser.os.habits.application.ports.out.medicion.ContarMedicionesAutomaticasPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@link ContarMedicionesAutomaticasPort} con {@link JdbcClient}, igual que {@code SumarMedicionesJdbcAdapter}: una agregación
 * para el ranking. La UNIQUE {@code (participante_id, habito_id, fecha_ejecucion)} sirve de índice por participante.
 */
@Component
class MedicionesAutomaticasJdbcAdapter implements ContarMedicionesAutomaticasPort {

    private static final String SQL = """
            SELECT DISTINCT r.participante_id AS participante_id
            FROM renaser.registros_habito r
            JOIN renaser.habitos h ON h.id = r.habito_id
            WHERE h.clave_sistema = :clave
              AND r.participante_id IN (:participantes)
              AND r.fecha_ejecucion <= :hasta
              AND r.estado = 'COMPLETADO'
              AND r.origen_medicion IN (:origenes)
            """;

    private final JdbcClient jdbcClient;

    MedicionesAutomaticasJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<UserId> conAlgunaMedicionAutomatica(Collection<UserId> participantes, String claveSistema,
                                                   LocalDate hasta) {
        return conAlgunaDe(participantes, claveSistema, hasta, List.of("HEALTH_CONNECT"));
    }

    @Override
    public Set<UserId> conAlgunaMedicionDelTelefono(Collection<UserId> participantes, String claveSistema, LocalDate hasta) {
        return conAlgunaDe(participantes, claveSistema, hasta, List.of("HEALTH_CONNECT", "GPS"));
    }

    private Set<UserId> conAlgunaDe(Collection<UserId> participantes, String claveSistema, LocalDate hasta,
                                    List<String> origenes) {
        if (participantes.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = participantes.stream().map(UserId::value).distinct().toList();
        return new HashSet<>(jdbcClient.sql(SQL).param("clave", claveSistema).param("participantes", ids)
                .param("hasta", hasta).param("origenes", origenes)
                .query((rs, fila) -> UserId.of(rs.getObject("participante_id", UUID.class)))
                .list());
    }
}
