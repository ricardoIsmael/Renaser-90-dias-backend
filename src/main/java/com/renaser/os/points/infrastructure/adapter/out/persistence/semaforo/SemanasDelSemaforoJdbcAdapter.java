package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo;

import com.renaser.os.points.application.ports.out.semaforo.SemanasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.FotoSemanal;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code semaforo_semanas} con {@link JdbcClient}. APPEND-ONLY (V68): el único INSERT usa
 * {@code ON CONFLICT DO NOTHING} y devuelve si de verdad insertó — así una segunda corrida del
 * barrido nunca reescribe la foto ni vuelve a avisar. No hay UPDATE ni DELETE.
 */
@Component
class SemanasDelSemaforoJdbcAdapter implements SemanasDelSemaforoPort {

    private static final String COLUMNAS =
            "participante_id, semana_hasta, semana_desde, porcentaje, dias_con_datos, dias_medidos, version_formula, cerrada_en";

    private static final String REGISTRAR = "INSERT INTO renaser.semaforo_semanas (" + COLUMNAS + """
            ) VALUES (:participante, :semanaHasta, :semanaDesde, :porcentaje, :diasConDatos, :diasMedidos,
                      :versionFormula, :cerradaEn)
            ON CONFLICT (participante_id, semana_hasta) DO NOTHING
            """;

    private final JdbcClient jdbcClient;

    SemanasDelSemaforoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<UserId, LocalDate> ultimaCerradaDe(Collection<UserId> participantes) {
        Map<UserId, LocalDate> resultado = new LinkedHashMap<>();
        if (participantes.isEmpty()) {
            return resultado;
        }
        jdbcClient.sql("""
                        SELECT participante_id, max(semana_hasta) AS ultima FROM renaser.semaforo_semanas
                        WHERE participante_id IN (:participantes) GROUP BY participante_id
                        """)
                .param("participantes", ids(participantes))
                .query((rs, n) -> resultado.put(UserId.of(rs.getObject("participante_id", UUID.class)),
                        rs.getObject("ultima", LocalDate.class)))
                .list();
        return resultado;
    }

    @Override
    public Map<UserId, FotoSemanal> deLaSemana(Collection<UserId> participantes, LocalDate semanaHasta) {
        Map<UserId, FotoSemanal> resultado = new LinkedHashMap<>();
        if (participantes.isEmpty()) {
            return resultado;
        }
        jdbcClient.sql("SELECT " + COLUMNAS + " FROM renaser.semaforo_semanas "
                        + "WHERE participante_id IN (:participantes) AND semana_hasta = :semanaHasta")
                .param("participantes", ids(participantes))
                .param("semanaHasta", semanaHasta)
                .query((rs, n) -> resultado.put(UserId.of(rs.getObject("participante_id", UUID.class)), foto(rs)))
                .list();
        return resultado;
    }

    @Override
    public List<FotoSemanal> ultimasDe(UserId participante, int cantidad) {
        List<FotoSemanal> masNuevaPrimero = new ArrayList<>(jdbcClient.sql("SELECT " + COLUMNAS
                        + " FROM renaser.semaforo_semanas WHERE participante_id = :participante"
                        + " ORDER BY semana_hasta DESC LIMIT :cantidad")
                .param("participante", participante.value())
                .param("cantidad", cantidad)
                .query((rs, n) -> foto(rs))
                .list());
        Collections.reverse(masNuevaPrimero);
        return masNuevaPrimero;
    }

    @Override
    public boolean registrar(UserId participante, FotoSemanal foto) {
        return jdbcClient.sql(REGISTRAR)
                .param("participante", participante.value())
                .param("semanaHasta", foto.semanaHasta())
                .param("semanaDesde", foto.semanaDesde())
                .param("porcentaje", foto.porcentaje())
                .param("diasConDatos", foto.diasConDatos())
                .param("diasMedidos", foto.diasMedidos())
                .param("versionFormula", foto.versionFormula())
                .param("cerradaEn", Timestamp.from(foto.cerradaEn()))
                .update() == 1;
    }

    private static List<UUID> ids(Collection<UserId> participantes) {
        return participantes.stream().map(UserId::value).distinct().toList();
    }

    private static FotoSemanal foto(ResultSet rs) throws SQLException {
        return new FotoSemanal(rs.getObject("semana_hasta", LocalDate.class), rs.getObject("semana_desde", LocalDate.class),
                rs.getBigDecimal("porcentaje"), rs.getInt("dias_con_datos"), rs.getInt("dias_medidos"),
                rs.getString("version_formula"), rs.getTimestamp("cerrada_en").toInstant());
    }
}
