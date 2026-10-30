package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.MiRadarGuardadoPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@code participantes_programa.mi_radar_encendido} y {@code dias_mi_radar} (V122) con {@link JdbcClient}: dos columnas de
 * una tabla que la entidad JPA de la fila no mapea, así que guardarlas no pisa nada de la participación ni al revés.
 *
 * <p>Los días viajan como un arreglo JSON ({@code [{"dia":6,"modo":"PERSONALIZADO","veces":3,"sinRadar":false}]}) hacia un
 * {@code jsonb}: son a lo sumo siete elementos que siempre se leen y se escriben juntos. {@code NULL} es «ningún día
 * propio».
 */
@Component
class MiRadarGuardadoJdbcAdapter implements MiRadarGuardadoPort {

    private static final String LEER = """
            SELECT mi_radar_encendido AS encendido, CAST(dias_mi_radar AS text) AS dias
            FROM renaser.participantes_programa
            WHERE usuario_id = :participante
            """;

    private static final String GUARDAR = """
            UPDATE renaser.participantes_programa
            SET mi_radar_encendido = :encendido,
                dias_mi_radar = CAST(:dias AS jsonb)
            WHERE usuario_id = :participante
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<DiaGuardado>> DIAS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;

    MiRadarGuardadoJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MiRadarGuardado de(UserId participanteId) {
        List<MiRadarGuardado> filas = jdbc.sql(LEER).param("participante", participanteId.value())
                .query((rs, fila) -> leer(rs)).list();
        if (filas.isEmpty()) {
            throw sinPrograma(participanteId);
        }
        return filas.getFirst();
    }

    @Override
    public void guardar(UserId participanteId, MiRadarGuardado guardado) {
        String dias = guardado.dias().isEmpty() ? null : JSON.writeValueAsString(guardado.dias());
        int filas = jdbc.sql(GUARDAR).param("participante", participanteId.value())
                .param("encendido", guardado.encendido()).param("dias", dias).update();
        if (filas == 0) {
            throw sinPrograma(participanteId);
        }
    }

    private static MiRadarGuardado leer(ResultSet rs) throws SQLException {
        String dias = rs.getString("dias");
        return new MiRadarGuardado(rs.getBoolean("encendido"), dias == null ? List.of() : JSON.readValue(dias, DIAS));
    }

    private static NoSuchElementException sinPrograma(UserId participanteId) {
        return new NoSuchElementException("Participante no encontrado: " + participanteId);
    }
}
