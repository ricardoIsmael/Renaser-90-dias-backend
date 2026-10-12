package com.renaser.os.habits.infrastructure.adapter.out.persistence.audiencia;

import com.renaser.os.habits.application.ports.out.audiencia.HistorialDeRolPort;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.audiencia.HistorialDeRol;
import com.renaser.os.habits.domain.model.audiencia.TramoDeRol;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * {@code historial_rol_participante} (V96). Las dos escrituras son idempotentes por la PK
 * {@code (usuario_id, rige_desde)}: si Modulith reentrega el evento, no se duplica nada.
 */
@Component
class HistorialRolJdbcAdapter implements HistorialDeRolPort {

    private static final String SELECT = """
            SELECT rol, rige_desde FROM renaser.historial_rol_participante WHERE usuario_id = :persona
            """;

    /** El rol anterior "desde siempre", solo si la persona todavía no tiene historia. */
    private static final String INSERT_ANTERIOR = """
            INSERT INTO renaser.historial_rol_participante (usuario_id, rige_desde, rol)
            SELECT :persona, :desdeSiempre, :rol
            WHERE NOT EXISTS (SELECT 1 FROM renaser.historial_rol_participante WHERE usuario_id = :persona)
            ON CONFLICT DO NOTHING
            """;

    private static final String INSERT_NUEVO = """
            INSERT INTO renaser.historial_rol_participante (usuario_id, rige_desde, rol)
            VALUES (:persona, :cuando, :rol)
            ON CONFLICT DO NOTHING
            """;

    private final JdbcClient jdbcClient;

    HistorialRolJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public HistorialDeRol de(UserId persona) {
        return HistorialDeRol.de(jdbcClient.sql(SELECT)
                .param("persona", persona.value())
                .query((rs, fila) -> new TramoDeRol(CodigoDeRol.of(rs.getString("rol")),
                        AudienciaHabitoJdbcAdapter.instante(rs, "rige_desde")))
                .list());
    }

    @Override
    public void anotarCambio(UserId persona, CodigoDeRol anterior, CodigoDeRol nuevo, Instant cuando) {
        insertar(INSERT_ANTERIOR, persona, anterior, "desdeSiempre", TramoDeRol.DESDE_SIEMPRE);
        insertar(INSERT_NUEVO, persona, nuevo, "cuando", cuando);
    }

    private void insertar(String sql, UserId persona, CodigoDeRol rol, String parametroDeInstante, Instant instante) {
        jdbcClient.sql(sql)
                .param("persona", persona.value())
                .param("rol", rol.valor())
                .param(parametroDeInstante, AudienciaHabitoJdbcAdapter.enUtc(instante))
                .update();
    }
}
