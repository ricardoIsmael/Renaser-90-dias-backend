package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo;

import com.renaser.os.points.application.ports.out.semaforo.PausasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.points.domain.model.semaforo.PausaId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code semaforo_pausas} con {@link JdbcClient}, mapeo a mano (mismo criterio que los otros dos). */
@Component
class PausasDelSemaforoJdbcAdapter implements PausasDelSemaforoPort {

    private static final String DE = """
            SELECT id, usuario_id, desde, hasta, reanudada_el, creada_en, reanudada_en
            FROM renaser.semaforo_pausas WHERE usuario_id IN (:usuarios) ORDER BY desde, creada_en
            """;

    private static final String GUARDAR = """
            INSERT INTO renaser.semaforo_pausas (id, usuario_id, desde, hasta, reanudada_el, creada_en, reanudada_en)
            VALUES (:id, :usuario, :desde, :hasta, :reanudadaEl, :creadaEn, :reanudadaEn)
            ON CONFLICT (id) DO UPDATE
               SET hasta = EXCLUDED.hasta, reanudada_el = EXCLUDED.reanudada_el, reanudada_en = EXCLUDED.reanudada_en
            """;

    private final JdbcClient jdbcClient;

    PausasDelSemaforoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<UserId, List<PausaDeMedicion>> de(Collection<UserId> usuarios) {
        Map<UserId, List<PausaDeMedicion>> resultado = new LinkedHashMap<>();
        if (usuarios.isEmpty()) {
            return resultado;
        }
        jdbcClient.sql(DE)
                .param("usuarios", usuarios.stream().map(UserId::value).distinct().toList())
                .query((rs, n) -> {
                    PausaDeMedicion pausa = pausa(rs);
                    resultado.computeIfAbsent(pausa.usuarioId(), k -> new ArrayList<>()).add(pausa);
                    return pausa;
                })
                .list();
        return resultado;
    }

    @Override
    public void guardar(PausaDeMedicion pausa) {
        jdbcClient.sql(GUARDAR)
                .param("id", pausa.id().value())
                .param("usuario", pausa.usuarioId().value())
                .param("desde", pausa.desde())
                .param("hasta", pausa.hasta())
                .param("reanudadaEl", pausa.reanudadaEl())
                .param("creadaEn", Timestamp.from(pausa.creadaEn()))
                .param("reanudadaEn", pausa.reanudadaEn() == null ? null : Timestamp.from(pausa.reanudadaEn()))
                .update();
    }

    private static PausaDeMedicion pausa(ResultSet rs) throws SQLException {
        Timestamp reanudadaEn = rs.getTimestamp("reanudada_en");
        return PausaDeMedicion.rehidratar(PausaId.of(rs.getObject("id", UUID.class)),
                UserId.of(rs.getObject("usuario_id", UUID.class)), rs.getObject("desde", LocalDate.class),
                rs.getObject("hasta", LocalDate.class), rs.getObject("reanudada_el", LocalDate.class),
                rs.getTimestamp("creada_en").toInstant(), reanudadaEn == null ? null : reanudadaEn.toInstant());
    }
}
