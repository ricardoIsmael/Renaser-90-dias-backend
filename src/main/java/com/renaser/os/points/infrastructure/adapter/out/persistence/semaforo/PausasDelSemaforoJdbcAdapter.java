package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo;

import com.renaser.os.points.application.ports.out.semaforo.PausasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.MotivoDePausa;
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

/**
 * {@code semaforo_pausas} con {@link JdbcClient}, mapeo a mano (mismo criterio que los otros dos). Guarda
 * los dos motivos: la pausa que pide el staff y los días con la cuenta suspendida (V72, D-209).
 *
 * <p><b>Una pausa terminada no se vuelve a abrir.</b> El upsert conserva {@code reanudada_el} y
 * {@code reanudada_en} si ya estaban: el dominio nunca las borra, y así una entrega repetida del evento
 * de la suspensión (el outbox entrega al menos una vez) que llegue después de la reactivación no deja
 * a la persona sin medir para siempre.
 */
@Component
class PausasDelSemaforoJdbcAdapter implements PausasDelSemaforoPort {

    private static final String DE = """
            SELECT id, usuario_id, motivo, desde, hasta, reanudada_el, creada_en, reanudada_en
            FROM renaser.semaforo_pausas WHERE usuario_id IN (:usuarios) ORDER BY desde, creada_en
            """;

    private static final String GUARDAR = """
            INSERT INTO renaser.semaforo_pausas (id, usuario_id, motivo, desde, hasta, reanudada_el, creada_en,
                                                 reanudada_en)
            VALUES (:id, :usuario, :motivo, :desde, :hasta, :reanudadaEl, :creadaEn, :reanudadaEn)
            ON CONFLICT (id) DO UPDATE
               SET hasta = EXCLUDED.hasta,
                   reanudada_el = COALESCE(renaser.semaforo_pausas.reanudada_el, EXCLUDED.reanudada_el),
                   reanudada_en = COALESCE(renaser.semaforo_pausas.reanudada_en, EXCLUDED.reanudada_en)
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
                .param("motivo", pausa.motivo().name())
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
                UserId.of(rs.getObject("usuario_id", UUID.class)), MotivoDePausa.valueOf(rs.getString("motivo")),
                rs.getObject("desde", LocalDate.class), rs.getObject("hasta", LocalDate.class),
                rs.getObject("reanudada_el", LocalDate.class), rs.getTimestamp("creada_en").toInstant(),
                reanudadaEn == null ? null : reanudadaEn.toInstant());
    }
}
