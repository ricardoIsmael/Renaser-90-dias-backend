package com.renaser.os.plataforma.infrastructure.adapter.out.persistence;

import com.renaser.os.plataforma.application.ports.out.ConsultarAuditoriaPort;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code cambios_auditados} (V101): solo INSERT y SELECT. La base además rechaza UPDATE y DELETE con un trigger, así
 * que ni un error de código ni un acceso directo con la credencial de la app pueden reescribir la historia.
 */
@Component
class AuditoriaJdbcAdapter implements RegistrarCambioAuditadoPort, ConsultarAuditoriaPort {

    private static final String INSERTAR = """
            INSERT INTO renaser.cambios_auditados
                   (id, tipo, actor_id, clave, antes, despues, motivo, dispositivo, creado_en)
            VALUES (:id, :tipo, :actor, :clave, CAST(:antes AS jsonb), CAST(:despues AS jsonb), :motivo,
                    :dispositivo, :en)
            """;
    private static final String FILTRO = """
             WHERE (CAST(:clave AS text) IS NULL OR clave = :clave)
               AND (CAST(:actor AS uuid) IS NULL OR actor_id = :actor)
               AND (CAST(:desde AS timestamptz) IS NULL OR creado_en >= :desde)
               AND (CAST(:hasta AS timestamptz) IS NULL OR creado_en < :hasta)
            """;
    private static final String PAGINA = "SELECT id, tipo, actor_id, clave, antes::text AS antes, "
            + "despues::text AS despues, motivo, dispositivo, creado_en FROM renaser.cambios_auditados" + FILTRO
            + " ORDER BY creado_en DESC, id LIMIT :limite OFFSET :desde_fila";
    private static final String TOTAL = "SELECT count(*) FROM renaser.cambios_auditados" + FILTRO;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, Object>> MAPA = new TypeReference<>() {
    };

    private final JdbcClient jdbc;

    AuditoriaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(CambioAuditado cambio) {
        jdbc.sql(INSERTAR).param("id", cambio.id()).param("tipo", cambio.tipo().name())
                .param("actor", cambio.actor() == null ? null : cambio.actor().value())
                .param("clave", cambio.clave()).param("antes", json(cambio.antes()))
                .param("despues", json(cambio.despues())).param("motivo", cambio.motivo())
                .param("dispositivo", cambio.dispositivo()).param("en", Timestamp.from(cambio.creadoEn()))
                .update();
    }

    @Override
    public PaginaDeCambios buscar(FiltroDeAuditoria filtro) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("clave", filtro.clave());
        parametros.put("actor", filtro.actor() == null ? null : filtro.actor().value());
        parametros.put("desde", filtro.desde() == null ? null : Timestamp.from(filtro.desde()));
        parametros.put("hasta", filtro.hasta() == null ? null : Timestamp.from(filtro.hasta()));
        long total = jdbc.sql(TOTAL).params(parametros).query(Long.class).single();
        parametros.put("limite", filtro.tamano());
        parametros.put("desde_fila", (long) filtro.pagina() * filtro.tamano());
        List<CambioAuditado> contenido = new ArrayList<>(jdbc.sql(PAGINA).params(parametros)
                .query((rs, n) -> leer(rs)).list());
        return new PaginaDeCambios(contenido, filtro.pagina(), total);
    }

    private static CambioAuditado leer(ResultSet rs) throws SQLException {
        UUID actor = rs.getObject("actor_id", UUID.class);
        return new CambioAuditado(rs.getObject("id", UUID.class), TipoDeCambio.valueOf(rs.getString("tipo")),
                actor == null ? null : UserId.of(actor), rs.getString("clave"), mapa(rs.getString("antes")),
                mapa(rs.getString("despues")), rs.getString("motivo"), rs.getString("dispositivo"),
                rs.getTimestamp("creado_en").toInstant());
    }

    private static String json(Map<String, Object> valores) {
        return valores == null ? null : JSON.writeValueAsString(valores);
    }

    private static Map<String, Object> mapa(String json) {
        return json == null ? null : JSON.readValue(json, MAPA);
    }
}
