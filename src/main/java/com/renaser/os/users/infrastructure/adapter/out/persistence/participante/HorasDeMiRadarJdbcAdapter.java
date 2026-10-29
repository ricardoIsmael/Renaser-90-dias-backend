package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.HorasDeMiRadarPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * {@code participantes_programa.horas_mi_radar} y {@code dias_mi_radar} (V119) con {@link JdbcClient}: dos columnas de
 * una tabla que la entidad JPA de la fila no mapea, así que guardarlas no pisa nada de la participación ni al revés.
 *
 * <p>Las horas de todos los días viajan como texto separado por comas y las arman {@code array_to_string} /
 * {@code string_to_array}: no hace falta un {@code java.sql.Array} (mismo criterio que
 * {@code AudienciaHabitoJdbcAdapter}). Los días con horas propias viajan como un arreglo JSON
 * ({@code [{"dia":6,"horas":[11]},{"dia":7,"horas":[]}]}) hacia un {@code jsonb}: son a lo sumo siete elementos que
 * siempre se leen y se escriben juntos. {@code NULL} en las horas es apagado; {@code NULL} en los días, ninguno propio.
 */
@Component
class HorasDeMiRadarJdbcAdapter implements HorasDeMiRadarPort {

    private static final String LEER = """
            SELECT array_to_string(horas_mi_radar, ',') AS horas, CAST(dias_mi_radar AS text) AS dias
            FROM renaser.participantes_programa
            WHERE usuario_id = :participante
            """;

    private static final String GUARDAR = """
            UPDATE renaser.participantes_programa
            SET horas_mi_radar = CAST(string_to_array(CAST(:horas AS text), ',') AS smallint[]),
                dias_mi_radar = CAST(:dias AS jsonb)
            WHERE usuario_id = :participante
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<HorasDeUnDia>> DIAS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;

    HorasDeMiRadarJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HorasDeMiRadar horasDe(UserId participanteId) {
        List<HorasDeMiRadar> filas = jdbc.sql(LEER).param("participante", participanteId.value())
                .query((rs, fila) -> leer(rs)).list();
        if (filas.isEmpty()) {
            throw sinPrograma(participanteId);
        }
        return filas.getFirst();
    }

    @Override
    public void guardarHoras(UserId participanteId, HorasDeMiRadar guardado) {
        boolean apagado = guardado.horas().isEmpty();
        String horas = apagado ? null
                : guardado.horas().stream().map(String::valueOf).collect(Collectors.joining(","));
        String dias = apagado || guardado.dias().isEmpty() ? null : JSON.writeValueAsString(guardado.dias());
        int filas = jdbc.sql(GUARDAR).param("participante", participanteId.value()).param("horas", horas)
                .param("dias", dias).update();
        if (filas == 0) {
            throw sinPrograma(participanteId);
        }
    }

    private static HorasDeMiRadar leer(ResultSet rs) throws SQLException {
        String horas = rs.getString("horas");
        String dias = rs.getString("dias");
        return new HorasDeMiRadar(
                horas == null ? List.of() : Arrays.stream(horas.split(",")).map(Integer::valueOf).toList(),
                dias == null ? List.of() : JSON.readValue(dias, DIAS));
    }

    private static NoSuchElementException sinPrograma(UserId participanteId) {
        return new NoSuchElementException("Participante no encontrado: " + participanteId);
    }
}
