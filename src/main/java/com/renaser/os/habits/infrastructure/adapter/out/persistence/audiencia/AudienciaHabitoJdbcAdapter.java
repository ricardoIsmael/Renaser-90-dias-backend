package com.renaser.os.habits.infrastructure.adapter.out.persistence.audiencia;

import com.renaser.os.habits.application.ports.out.audiencia.CargarAudienciasPort;
import com.renaser.os.habits.application.ports.out.audiencia.GuardarAudienciaPort;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.audiencia.HistorialDeAudiencia;
import com.renaser.os.habits.domain.model.audiencia.VersionDeAudiencia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code audiencias_habito} (V96) con {@link JdbcClient}: tabla de {@code habits}, una consulta por lote. Los roles
 * viajan como texto separado por comas y los arma {@code string_to_array}: un código de rol nunca lleva comas
 * ({@link CodigoDeRol} solo admite mayúsculas y guion bajo), y así no hace falta un {@code java.sql.Array}.
 */
@Component
class AudienciaHabitoJdbcAdapter implements CargarAudienciasPort, GuardarAudienciaPort {

    private static final String SELECT = """
            SELECT habito_id, roles, rige_desde, cambiado_por, cambiado_en
            FROM renaser.audiencias_habito
            WHERE habito_id IN (:habitos)
            """;

    private static final String INSERT = """
            INSERT INTO renaser.audiencias_habito (id, habito_id, roles, rige_desde, cambiado_por, cambiado_en)
            VALUES (:id, :habito, string_to_array(:roles, ','), :rigeDesde, :cambiadoPor, :cambiadoEn)
            """;

    private final JdbcClient jdbcClient;
    private final IdGenerator idGenerator;

    AudienciaHabitoJdbcAdapter(JdbcClient jdbcClient, IdGenerator idGenerator) {
        this.jdbcClient = jdbcClient;
        this.idGenerator = idGenerator;
    }

    @Override
    public Map<HabitoId, HistorialDeAudiencia> deHabitos(Collection<HabitoId> habitos) {
        if (habitos.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql(SELECT)
                .param("habitos", habitos.stream().map(HabitoId::value).distinct().toList())
                .query(AudienciaHabitoJdbcAdapter::mapear)
                .list().stream()
                .collect(Collectors.groupingBy(VersionDeAudiencia::habitoId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> HistorialDeAudiencia.de(e.getValue())));
    }

    @Override
    public void agregarVersion(VersionDeAudiencia version) {
        jdbcClient.sql(INSERT)
                .param("id", idGenerator.newId())
                .param("habito", version.habitoId().value())
                .param("roles", version.audiencia().roles().stream().map(CodigoDeRol::valor).sorted()
                        .collect(Collectors.joining(",")))
                .param("rigeDesde", enUtc(version.rigeDesde()))
                .param("cambiadoPor", version.cambiadoPor().value())
                .param("cambiadoEn", enUtc(version.cambiadoEn()))
                .update();
    }

    private static VersionDeAudiencia mapear(ResultSet rs, int fila) throws SQLException {
        return new VersionDeAudiencia(HabitoId.of(rs.getObject("habito_id", UUID.class)), audiencia(rs.getArray("roles")),
                instante(rs, "rige_desde"), UserId.of(rs.getObject("cambiado_por", UUID.class)),
                instante(rs, "cambiado_en"));
    }

    private static Audiencia audiencia(Array roles) throws SQLException {
        return Audiencia.de(Arrays.stream((String[]) roles.getArray()).map(CodigoDeRol::of)
                .collect(Collectors.toSet()));
    }

    static Instant instante(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, OffsetDateTime.class).toInstant();
    }

    static OffsetDateTime enUtc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
