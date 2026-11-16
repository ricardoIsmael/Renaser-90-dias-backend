package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.pagos;

import com.renaser.os.onboarding.application.ports.out.pagos.CronogramasDeTodosPort;
import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeCuota;
import com.renaser.os.onboarding.domain.model.pagos.ModalidadDePago;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.onboarding.domain.model.pagos.PagoDeLaPersona;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Los cronogramas de muchas personas en Postgres (D-338): {@code estado_onboarding} (modalidad, moneda y confirmación) y {@code cuotas_de_pago}, las dos tablas de
 * este módulo. Cada método es UNA consulta para todas las personas pedidas; las cuotas usan el índice {@code cuotas_de_pago_persona_idx (usuario_id, vence_el)}.
 */
@Component
class CronogramasDeTodosJdbcAdapter implements CronogramasDeTodosPort {

    private static final String CON_CRONOGRAMA = "SELECT e.usuario_id FROM renaser.estado_onboarding e WHERE e.pago_modalidad = 'CON_CRONOGRAMA'";

    private static final String PAGOS = """
            SELECT usuario_id, pago_modalidad, pago_moneda, cronograma_confirmado_en, cronograma_confirmado_nombre, cronograma_confirmado_huella
            FROM renaser.estado_onboarding
            WHERE usuario_id IN (:ids) AND pago_modalidad = 'CON_CRONOGRAMA'
            """;

    private static final String CUOTAS = """
            SELECT id, usuario_id, vence_el, monto, estado, estado_cambiado_por, estado_cambiado_en, creada_en
            FROM renaser.cuotas_de_pago
            WHERE usuario_id IN (:ids)
            ORDER BY usuario_id, vence_el, creada_en, id
            """;

    private final JdbcClient jdbc;

    CronogramasDeTodosJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UserId> personasConCronograma(FiltroDeCuotas filtro) {
        StringBuilder sql = new StringBuilder(CON_CRONOGRAMA);
        Map<String, Object> parametros = new HashMap<>();
        if (filtro.recorta()) {
            sql.append(" AND EXISTS (SELECT 1 FROM renaser.cuotas_de_pago c WHERE c.usuario_id = e.usuario_id").append(condiciones(filtro, parametros)).append(")");
        }
        sql.append(" ORDER BY e.usuario_id");
        return jdbc.sql(sql.toString()).params(parametros).query(UUID.class).list().stream().map(UserId::of).toList();
    }

    @Override
    public Map<UserId, CronogramaGuardado> deLasPersonas(Collection<UserId> personas) {
        if (personas.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = personas.stream().map(UserId::value).distinct().toList();
        List<Map.Entry<UserId, PagoDeLaPersona>> pagos = jdbc.sql(PAGOS).param("ids", ids)
                .query((rs, n) -> Map.entry(UserId.of(rs.getObject("usuario_id", UUID.class)), pago(rs))).list();
        Map<UserId, List<Cuota>> cuotas = jdbc.sql(CUOTAS).param("ids", ids)
                .query((rs, n) -> Map.entry(UserId.of(rs.getObject("usuario_id", UUID.class)), cuota(rs))).list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toCollection(ArrayList::new))));
        Map<UserId, CronogramaGuardado> guardados = new LinkedHashMap<>();
        pagos.forEach(fila -> guardados.put(fila.getKey(), new CronogramaGuardado(fila.getValue(), List.copyOf(cuotas.getOrDefault(fila.getKey(), List.of())))));
        return guardados;
    }

    /** Las condiciones del filtro sobre {@code c}, con sus parámetros: solo las que vienen (sin {@code :x IS NULL}, que Postgres no sabe tipar). */
    private static String condiciones(FiltroDeCuotas filtro, Map<String, Object> parametros) {
        StringBuilder sql = new StringBuilder();
        if (filtro.desde() != null) {
            sql.append(" AND c.vence_el >= :desde");
            parametros.put("desde", filtro.desde());
        }
        if (filtro.hasta() != null) {
            sql.append(" AND c.vence_el <= :hasta");
            parametros.put("hasta", filtro.hasta());
        }
        if (filtro.estado() != null) {
            sql.append(" AND c.estado = :estado");
            parametros.put("estado", filtro.estado().name());
        }
        return sql.toString();
    }

    private static PagoDeLaPersona pago(ResultSet rs) throws SQLException {
        String moneda = rs.getString("pago_moneda");
        return new PagoDeLaPersona(ModalidadDePago.valueOf(rs.getString("pago_modalidad")), moneda == null ? null : Moneda.valueOf(moneda),
                instante(rs.getTimestamp("cronograma_confirmado_en")), rs.getString("cronograma_confirmado_nombre"), rs.getString("cronograma_confirmado_huella"));
    }

    private static Cuota cuota(ResultSet rs) throws SQLException {
        UUID marcadaPor = rs.getObject("estado_cambiado_por", UUID.class);
        return new Cuota(rs.getObject("id", UUID.class), rs.getObject("vence_el", java.time.LocalDate.class), rs.getBigDecimal("monto"),
                EstadoDeCuota.valueOf(rs.getString("estado")), marcadaPor == null ? null : UserId.of(marcadaPor), instante(rs.getTimestamp("estado_cambiado_en")),
                instante(rs.getTimestamp("creada_en")));
    }

    private static Instant instante(Timestamp marca) {
        return marca == null ? null : marca.toInstant();
    }
}
