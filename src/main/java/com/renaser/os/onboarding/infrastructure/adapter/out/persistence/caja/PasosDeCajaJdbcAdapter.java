package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.caja;

import com.renaser.os.onboarding.application.ports.out.caja.PasosDeCajaPort;
import com.renaser.os.onboarding.domain.model.caja.PasoDeCaja;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Los pasos de la caja en {@code etapas_onboarding_completadas} (V41 + V82) con {@link JdbcClient}: la
 * entidad JPA de esa tabla es la del Mapa y no conoce las columnas nuevas; acá hacen falta el
 * {@code ON CONFLICT} y la lectura en lote, que JPA no da.
 */
@Component
class PasosDeCajaJdbcAdapter implements PasosDeCajaPort {

    private static final String COLUMNAS = "usuario_id, flujo, completado_en, marcada_por, detalle::text AS detalle";
    private static final String DE_APRENDIZ = "SELECT " + COLUMNAS + """
             FROM renaser.etapas_onboarding_completadas
            WHERE usuario_id = :usuario AND flujo LIKE 'caja:%'
            """;
    private static final String DE_APRENDICES = "SELECT " + COLUMNAS + """
             FROM renaser.etapas_onboarding_completadas
            WHERE usuario_id IN (:usuarios) AND flujo LIKE 'caja:%'
            """;
    private static final String INSERTAR = """
            INSERT INTO renaser.etapas_onboarding_completadas (usuario_id, flujo, completado_en, marcada_por, detalle)
            VALUES (:usuario, :flujo, :en, :por, CAST(:detalle AS jsonb))
            """;
    private static final String REEMPLAZAR = INSERTAR + """
            ON CONFLICT (usuario_id, flujo) DO UPDATE
               SET completado_en = EXCLUDED.completado_en, marcada_por = EXCLUDED.marcada_por,
                   detalle = EXCLUDED.detalle
            """;
    private static final String SI_FALTA = INSERTAR + " ON CONFLICT (usuario_id, flujo) DO NOTHING";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> OBJETO = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;

    PasosDeCajaJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<PasoDeCaja> deAprendiz(UserId aprendizId) {
        return jdbcClient.sql(DE_APRENDIZ).param("usuario", aprendizId.value())
                .query((rs, n) -> paso(rs)).list().stream().flatMap(Optional::stream).toList();
    }

    @Override
    public Map<UserId, List<PasoDeCaja>> deAprendices(Collection<UserId> aprendices) {
        Map<UserId, List<PasoDeCaja>> porAprendiz = new HashMap<>();
        if (aprendices.isEmpty()) {
            return porAprendiz;
        }
        jdbcClient.sql(DE_APRENDICES).param("usuarios", aprendices.stream().map(UserId::value).toList())
                .query((rs, n) -> paso(rs)).list().stream().flatMap(Optional::stream)
                .forEach(p -> porAprendiz.computeIfAbsent(p.aprendizId(), id -> new ArrayList<>()).add(p));
        return porAprendiz;
    }

    @Override
    public void registrar(PasoDeCaja paso) {
        conParametros(INSERTAR, paso).update();
    }

    @Override
    public void reemplazar(PasoDeCaja paso) {
        conParametros(REEMPLAZAR, paso).update();
    }

    @Override
    public boolean registrarSiFalta(PasoDeCaja paso) {
        return conParametros(SI_FALTA, paso).update() == 1;
    }

    private JdbcClient.StatementSpec conParametros(String sql, PasoDeCaja paso) {
        return jdbcClient.sql(sql)
                .param("usuario", paso.aprendizId().value())
                .param("flujo", paso.flujo())
                .param("en", Timestamp.from(paso.en()))
                .param("por", paso.marcadaPor() == null ? null : paso.marcadaPor().value(), Types.OTHER)
                .param("detalle", paso.detalle().isEmpty() ? null : JSON.writeValueAsString(paso.detalle()),
                        Types.VARCHAR);
    }

    private static Optional<PasoDeCaja> paso(ResultSet rs) throws SQLException {
        UUID por = rs.getObject("marcada_por", UUID.class);
        return PasoDeCaja.desdeFila(UserId.of(rs.getObject("usuario_id", UUID.class)), rs.getString("flujo"),
                rs.getTimestamp("completado_en").toInstant(), por == null ? null : UserId.of(por),
                detalle(rs.getString("detalle")));
    }

    /** Los valores se leen como texto: el dominio guarda todo el detalle como texto. */
    private static Map<String, String> detalle(String json) {
        Map<String, String> detalle = new HashMap<>();
        if (json != null) {
            JSON.readValue(json, OBJETO).forEach((clave, valor) -> {
                if (valor != null) {
                    detalle.put(clave, String.valueOf(valor));
                }
            });
        }
        return detalle;
    }
}
